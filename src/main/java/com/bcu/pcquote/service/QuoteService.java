package com.bcu.pcquote.service;

import com.bcu.pcquote.domain.Quote;
import com.bcu.pcquote.domain.QuoteItem;
import com.bcu.pcquote.domain.SurveyRequest;
import com.bcu.pcquote.dto.CandidatePart;
import com.bcu.pcquote.dto.GeminiBuild;
import com.bcu.pcquote.dto.QuoteResponse;
import com.bcu.pcquote.dto.SurveyRequestDto;
import com.bcu.pcquote.repository.CompatibilityRepository;
import com.bcu.pcquote.repository.CompatibilityValidator;
import com.bcu.pcquote.repository.QuoteItemRepository;
import com.bcu.pcquote.repository.QuoteRepository;
import com.bcu.pcquote.repository.SurveyRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 견적 추천 오케스트레이션 (파이프라인 전체를 잇는 계층).
 * 요청 저장 → 호환 후보군 조회 → 프롬프트 구성 → Gemini 3종 견적 → 검증/저장 → 응답.
 */
@Service
public class QuoteService {

    private static final Map<String, Integer> CATEGORY_ID = Map.of(
            "CPU", 1, "GPU", 2, "MAINBOARD", 3, "RAM", 4,
            "PSU", 5, "COOLER", 6, "STORAGE", 7, "CASE", 8);

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final CompatibilityRepository compatibilityRepository;
    private final CompatibilityValidator compatibilityValidator;
    private final GeminiService geminiService;
    private final SurveyRequestRepository surveyRequestRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteItemRepository quoteItemRepository;

    public QuoteService(CompatibilityRepository compatibilityRepository,
                        CompatibilityValidator compatibilityValidator,
                        GeminiService geminiService,
                        SurveyRequestRepository surveyRequestRepository,
                        QuoteRepository quoteRepository,
                        QuoteItemRepository quoteItemRepository) {
        this.compatibilityRepository = compatibilityRepository;
        this.compatibilityValidator = compatibilityValidator;
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

        // 3) 견적 생성 + 2차 호환성 검증 통과분만 확보 (위반 조합은 재구성/제외 → 추천 안 함)
        List<GeminiBuild> builds = generateCompatibleBuilds(dto, pool);

        // 4) 1차 가드레일(후보군 내 part_id) 저장 + 2차 가드레일(조합 호환성 재검증) + 응답 구성
        List<QuoteResponse.BuildView> views = new ArrayList<>();
        for (GeminiBuild b : builds) {
            Long cpuId = parseId(b.cpuPartId());
            Long gpuId = parseId(b.gpuPartId());
            Long mbId = parseId(b.mainboardPartId());
            Long ramId = parseId(b.ramPartId());
            Long psuId = parseId(b.psuPartId());
            Long coolerId = parseId(b.coolerPartId());
            Long storageId = parseId(b.storagePartId());
            Long caseId = parseId(b.casePartId());

            Quote quote = new Quote();
            quote.setRequestId(req.getRequestId());
            quote.setTier(b.tier());
            quote.setReason(b.reason());
            quote.setAiModel("gemini-2.5-flash");
            quoteRepository.save(quote);   // quote_id 확보

            List<QuoteResponse.ItemView> items = new ArrayList<>();
            int actualTotal = 0;           // 캐싱 실가격 기준 합계 (LLM 추정값 신뢰 X)
            for (Long partId : java.util.stream.Stream.of(
                        cpuId, gpuId, mbId, ramId, psuId, coolerId, storageId, caseId)
                    .filter(java.util.Objects::nonNull).toList()) {
                CandidatePart cp = byId.get(partId);
                if (cp == null) {
                    continue; // 1차 가드레일: 후보군에 없는 id → 제외
                }
                QuoteItem qi = new QuoteItem();
                qi.setQuoteId(quote.getQuoteId());
                qi.setPartId(partId);
                qi.setCategoryId(CATEGORY_ID.get(cp.category()));
                qi.setUnitPrice(cp.price());
                quoteItemRepository.save(qi);
                if (cp.price() != null) {
                    actualTotal += cp.price();
                }

                String name = (cp.manufacturer() == null ? "" : cp.manufacturer() + " ") + cp.modelName();
                items.add(new QuoteResponse.ItemView(cp.category(), partId, name, cp.price()));
            }
            quote.setTotalPrice(actualTotal);   // 실가격 합계로 저장
            quoteRepository.save(quote);

            // 예산 후처리: 실제 합계가 예산 상한을 넘는지
            boolean withinBudget = dto.budgetMax() == null || actualTotal <= dto.budgetMax();
            if (!withinBudget) {
                log.warn("[{}] 예산 초과 (requestId={}): 합계 {}원 > 예산 {}원",
                        b.tier(), req.getRequestId(), actualTotal, dto.budgetMax());
            }

            // 2차 가드레일: 조합 호환성 재검증 (시연2 규칙)
            List<String> warnings = compatibilityValidator.findViolations(
                    cpuId, gpuId, mbId, ramId, psuId, coolerId, caseId);
            boolean compatible = warnings.isEmpty();
            if (!compatible) {
                log.warn("[{}] 견적 호환성 위반 (requestId={}, quoteId={}): {}",
                        b.tier(), req.getRequestId(), quote.getQuoteId(), warnings);
            }

            views.add(new QuoteResponse.BuildView(
                    b.tier(), actualTotal, withinBudget, compatible, warnings, b.reason(), items));
        }

        req.setStatus("완료");
        surveyRequestRepository.save(req);
        return new QuoteResponse(req.getRequestId(), views);
    }

    /**
     * 견적을 생성하고 2차 호환성 검증을 통과한 것만 모은다.
     * 위반 견적이 있으면 그 위반 내용을 피드백으로 붙여 최대 3회까지 재구성을 요청하고,
     * 끝까지 호환되지 않는 견적은 응답에서 제외한다(추천하지 않음).
     */
    private List<GeminiBuild> generateCompatibleBuilds(SurveyRequestDto dto, List<CandidatePart> pool) {
        String basePrompt = buildPrompt(dto, pool);
        List<GeminiBuild> accepted = new ArrayList<>();
        Set<String> acceptedTiers = new HashSet<>();
        StringBuilder feedback = new StringBuilder();

        for (int attempt = 1; attempt <= 3; attempt++) {
            List<GeminiBuild> builds = geminiService.generateBuilds(basePrompt + feedback, pool);
            List<String> problems = new ArrayList<>();
            for (GeminiBuild b : builds) {
                if (b.tier() == null || acceptedTiers.contains(b.tier())) {
                    continue;
                }
                List<String> violations = validateBuild(b);
                if (violations.isEmpty()) {
                    accepted.add(b);
                    acceptedTiers.add(b.tier());
                } else {
                    problems.add(b.tier() + " → " + String.join(", ", violations));
                }
            }
            if (problems.isEmpty() || acceptedTiers.size() >= 4) {
                break;
            }
            log.warn("호환성 위반으로 재구성 {}/3: {}", attempt, problems);
            feedback.append("\n\n## 직전 시도 오류 (반드시 수정)\n")
                    .append("아래 견적은 부품 호환성 위반으로 거부되었다. 서로 완벽히 호환되는 조합으로 다시 구성하라:\n- ")
                    .append(String.join("\n- ", problems));
        }
        return accepted;
    }

    /** 한 견적의 조합 호환성 위반 목록 (빈 목록이면 호환 OK) */
    private List<String> validateBuild(GeminiBuild b) {
        return compatibilityValidator.findViolations(
                parseId(b.cpuPartId()), parseId(b.gpuPartId()), parseId(b.mainboardPartId()),
                parseId(b.ramPartId()), parseId(b.psuPartId()), parseId(b.coolerPartId()),
                parseId(b.casePartId()));
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
        sb.append("\n## 지시\n예산 내에서 아래 4가지 견적을 모두 구성하라. "
                + "각 부품은 후보군의 part_id 로 지정하고, total_price 와 자연어 reason 을 포함하라.\n"
                + "1) 가성비 — 외장 그래픽카드 포함, 목적 대비 가장 저렴한 조합\n"
                + "2) 안정성 — 외장 그래픽카드 포함, 전원부·발열·밸런스가 안정적인 조합\n"
                + "3) 최고성능 — 외장 그래픽카드 포함, 예산 내 최고 성능\n"
                + "4) 내장그래픽 — 외장 그래픽카드 없이(gpu_part_id 생략) 내장그래픽이 있는 CPU로만 구성. "
                + "reason 에는 '내장그래픽이 무엇인지, 어떤 용도(문서/웹/영상감상 등)에 적합하고 게이밍·영상편집엔 부족할 수 있음'을 "
                + "PC를 잘 모르는 사람도 이해하도록 쉽게 설명하라.");
        return sb.toString();
    }
}
