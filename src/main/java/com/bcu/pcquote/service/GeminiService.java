package com.bcu.pcquote.service;

import com.bcu.pcquote.dto.CandidatePart;
import com.bcu.pcquote.dto.GeminiBuild;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Google AI Studio(Gemini) 호출 (readme 2.3단계).
 * - thinkingBudget=0 으로 추론 비활성화 → 응답 24초 → 약 4초
 * - responseSchema 로 3종 견적 JSON 출력을 강제 (할루시네이션 방지)
 * - 각 부품 필드를 "후보군의 실제 part_id enum" 으로 하드 제약 → 없는 부품 생성 원천 차단
 * - Spring Boot 4 = Jackson 3(tools.jackson.databind)
 */
@Service
public class GeminiService {

    /** 스키마 필드명 → 후보군 카테고리 코드 매핑 */
    private static final Map<String, String> FIELD_TO_CATEGORY = new LinkedHashMap<>() {{
        put("cpu_part_id", "CPU");
        put("gpu_part_id", "GPU");
        put("mainboard_part_id", "MAINBOARD");
        put("ram_part_id", "RAM");
        put("psu_part_id", "PSU");
        put("cooler_part_id", "COOLER");
        put("storage_part_id", "STORAGE");
        put("case_part_id", "CASE");
    }};

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiService.class);

    private final RestClient rest;
    private final ObjectMapper om = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String fallbackModel;

    public GeminiService(@Value("${gemini.base-url}") String baseUrl,
                         @Value("${gemini.api-key}") String apiKey,
                         @Value("${gemini.model}") String model,
                         @Value("${gemini.fallback-model}") String fallbackModel) {
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModel = fallbackModel;
    }

    public List<GeminiBuild> generateBuilds(String userPrompt, List<CandidatePart> pool) {
        // 카테고리별 후보 part_id (문자열) → 스키마 enum 제약에 사용
        Map<String, List<String>> idsByCategory = pool.stream().collect(Collectors.groupingBy(
                CandidatePart::category,
                Collectors.mapping(p -> String.valueOf(p.partId()), Collectors.toList())));

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text",
                        "너는 PC 견적 전문가다. 반드시 후보군 목록에 있는 part_id 만 사용하고, "
                        + "소켓/램규격/폼팩터/GPU길이/쿨러높이/파워 권장출력 호환성을 지켜라. 예산을 초과하지 마라. "
                        + "외장 그래픽카드(gpu_part_id)는 선택이며, 내장그래픽이 있는 CPU는 GPU 없이 구성할 수 있다."))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema(idsByCategory),
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );

        String raw = callWithRetry(body);

        GenResp resp = om.readValue(raw, GenResp.class);
        if (resp.candidates() == null || resp.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini 응답에 candidates 없음: " + raw);
        }
        String text = resp.candidates().get(0).content().parts().get(0).text();
        return om.readValue(text, BuildsWrapper.class).builds();
    }

    /**
     * 429/5xx(과부하) 시 지수 백오프로 재시도하고, 소진되면 폴백 모델(flash-lite)로 전환.
     * 4xx(스키마 오류 등)는 즉시 실패시킨다.
     */
    private String callWithRetry(Map<String, Object> body) {
        RuntimeException last = null;
        for (String m : List.of(model, fallbackModel)) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    return rest.post()
                            .uri("/v1beta/models/{model}:generateContent?key={key}", m, apiKey)
                            .body(body)
                            .retrieve()
                            .body(String.class);
                } catch (org.springframework.web.client.RestClientResponseException e) {
                    int sc = e.getStatusCode().value();
                    last = e;
                    if (sc == 429 || sc >= 500) {
                        log.warn("Gemini {} 호출 실패({}) - 재시도 {}/3", m, sc, attempt);
                        sleep(400L * attempt);   // 백오프
                    } else {
                        throw e;                 // 4xx 는 재시도 무의미
                    }
                }
            }
        }
        throw new IllegalStateException("Gemini 호출 실패(재시도/폴백 소진)", last);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * builds[] 각 항목 = tier + 8개 부품(part_id enum) + total_price + reason.
     * 각 part_id 는 해당 카테고리의 실제 후보 id 만 허용하는 STRING enum 으로 하드 제약한다.
     */
    private Map<String, Object> responseSchema(Map<String, List<String>> idsByCategory) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tier", Map.of("type", "STRING",
                "enum", List.of("가성비", "안정성", "최고성능", "내장그래픽")));

        FIELD_TO_CATEGORY.forEach((field, category) -> {
            boolean isGpu = field.equals("gpu_part_id");
            List<String> ids = idsByCategory.get(category);
            if (ids == null || ids.isEmpty()) {
                props.put(field, Map.of("type", "STRING"));
            } else if (isGpu) {
                props.put(field, Map.of("type", "STRING", "enum", ids, "nullable", true)); // 외장 GPU 는 선택
            } else {
                props.put(field, Map.of("type", "STRING", "enum", ids));
            }
        });

        props.put("total_price", Map.of("type", "INTEGER"));
        props.put("reason", Map.of("type", "STRING"));

        // gpu_part_id 는 선택이므로 required 에서 제외
        List<String> required = new ArrayList<>(props.keySet());
        required.remove("gpu_part_id");

        Map<String, Object> item = Map.of(
                "type", "OBJECT",
                "properties", props,
                "required", required
        );
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of("builds", Map.of("type", "ARRAY", "items", item)),
                "required", List.of("builds")
        );
    }

    // --- Gemini 응답 구조 (필요한 필드만) ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenResp(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BuildsWrapper(List<GeminiBuild> builds) {}
}
