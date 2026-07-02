package com.bcu.pcquote.service;

import com.bcu.pcquote.domain.Quote;
import com.bcu.pcquote.domain.QuoteItem;
import com.bcu.pcquote.domain.SurveyRequest;
import com.bcu.pcquote.dto.CandidatePart;
import com.bcu.pcquote.dto.GeminiBuild;
import com.bcu.pcquote.dto.QuoteResponse;
import com.bcu.pcquote.dto.SurveyRequestDto;
import com.bcu.pcquote.repository.CompatibilityRepository;
import com.bcu.pcquote.repository.QuoteItemRepository;
import com.bcu.pcquote.repository.QuoteRepository;
import com.bcu.pcquote.repository.SurveyRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 견적 추천 오케스트레이션 (파이프라인 전체를 잇는 계층).
 * 요청 저장 → 호환 후보군 조회 → 프롬프트 구성 → Gemini 3종 견적 → 검증/저장 → 응답.
 */
@Service
public class QuoteService {

    private static final Map<String, Integer> CATEGORY_ID = Map.of(
            "CPU", 1, "GPU", 2, "MAINBOARD", 3, "RAM", 4,
            "PSU", 5, "COOLER", 6, "STORAGE", 7, "CASE", 8);

    private final CompatibilityRepository compatibilityRepository;
    private final GeminiService geminiService;
    private final SurveyRequestRepository surveyRequestRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteItemRepository quoteItemRepository;

    public QuoteService(CompatibilityRepository compatibilityRepository,
                        GeminiService geminiService,
                        SurveyRequestRepository surveyRequestRepository,
                        QuoteRepository quoteRepository,
                        QuoteItemRepository quoteItemRepository) {
        this.compatibilityRepository = compatibilityRepository;
        this.geminiService = geminiService;
        this.surveyRequestRepository = surveyRequestRepository;
        this.quoteRepository = quoteRepository;
        this.quoteItemRepository = quoteItemRepository;
    }

    @Transactional
    public QuoteResponse recommend(SurveyRequestDto dto) {
        // 1) 룰 기반 호환 후보군 Pool
        List<CandidatePart> pool = compatibilityRepository.findCandidatePool();
        Map<Long, CandidatePart> byId = pool.stream()
                .collect(Collectors.toMap(CandidatePart::partId, p -> p));

        // 2) 요청 저장
        SurveyRequest req = new SurveyRequest();
        req.setUserId(dto.userId());
        req.setPurpose(dto.purpose());
        req.setBudgetMin(dto.budgetMin());
        req.setBudgetMax(dto.budgetMax());
        req.setResolutionTarget(dto.resolutionTarget());
        req.setDetailRequirement(dto.detailRequirement());
        req.setStatus("처리중");
        surveyRequestRepository.save(req);

        // 3) 프롬프트 구성 + Gemini 3종 견적 생성 (후보 id enum 으로 제약)
        List<GeminiBuild> builds = geminiService.generateBuilds(buildPrompt(dto, pool), pool);

        // 4) 검증(후보군 내 part_id 인지) + 저장 + 응답 구성
        List<QuoteResponse.BuildView> views = new ArrayList<>();
        for (GeminiBuild b : builds) {
            Quote quote = new Quote();
            quote.setRequestId(req.getRequestId());
            quote.setTier(b.tier());
            quote.setTotalPrice(b.totalPrice());
            quote.setReason(b.reason());
            quote.setAiModel("gemini-2.5-flash");
            quoteRepository.save(quote);

            List<QuoteResponse.ItemView> items = new ArrayList<>();
            for (Long partId : orderedIds(b)) {
                CandidatePart cp = (partId == null) ? null : byId.get(partId);
                if (cp == null) {
                    continue; // 후보군에 없는 id → 가드레일로 제외
                }
                QuoteItem qi = new QuoteItem();
                qi.setQuoteId(quote.getQuoteId());
                qi.setPartId(partId);
                qi.setCategoryId(CATEGORY_ID.get(cp.category()));
                qi.setUnitPrice(cp.price());
                quoteItemRepository.save(qi);

                String name = (cp.manufacturer() == null ? "" : cp.manufacturer() + " ") + cp.modelName();
                items.add(new QuoteResponse.ItemView(cp.category(), partId, name, cp.price()));
            }
            views.add(new QuoteResponse.BuildView(b.tier(), b.totalPrice(), b.reason(), items));
        }

        req.setStatus("완료");
        surveyRequestRepository.save(req);
        return new QuoteResponse(req.getRequestId(), views);
    }

    private List<Long> orderedIds(GeminiBuild b) {
        return Stream.of(b.cpuPartId(), b.gpuPartId(), b.mainboardPartId(), b.ramPartId(),
                        b.psuPartId(), b.coolerPartId(), b.storagePartId(), b.casePartId())
                .map(this::parseId)
                .toList();
    }

    /** enum 제약 하에서도 방어적으로 파싱 (숫자 아니면 null → 가드레일이 제외) */
    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildPrompt(SurveyRequestDto dto, List<CandidatePart> pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 사용자 요구사항\n")
                .append("- 용도: ").append(dto.purpose()).append('\n')
                .append("- 예산: ").append(dto.budgetMin() == null ? "" : dto.budgetMin() + "~")
                .append(dto.budgetMax()).append("원\n")
                .append("- 해상도: ").append(dto.resolutionTarget()).append('\n')
                .append("- 상세: ").append(dto.detailRequirement() == null ? "" : dto.detailRequirement())
                .append("\n\n## 호환 부품 후보군 (이 목록의 part_id 만 사용)\n");

        Map<String, List<CandidatePart>> byCat = pool.stream()
                .collect(Collectors.groupingBy(CandidatePart::category));
        for (Map.Entry<String, List<CandidatePart>> e : byCat.entrySet()) {
            sb.append("### ").append(e.getKey()).append('\n');
            for (CandidatePart c : e.getValue()) {
                sb.append("- id=").append(c.partId()).append(" | ")
                        .append(c.manufacturer() == null ? "" : c.manufacturer() + " ").append(c.modelName())
                        .append(" | ").append(c.spec())
                        .append(" | 가격 ").append(c.price() == null ? "미정" : c.price() + "원").append('\n');
            }
        }
        sb.append("\n## 지시\n예산 내에서 가성비/안정성/최고성능 3종 견적을 구성하라. "
                + "각 부품은 후보군의 part_id 로 지정하고, total_price 와 자연어 reason 을 포함하라.");
        return sb.toString();
    }
}
