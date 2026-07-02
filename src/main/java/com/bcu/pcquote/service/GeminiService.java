package com.bcu.pcquote.service;

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

/**
 * Google AI Studio(Gemini) 호출 (readme 2.3단계).
 * - thinkingBudget=0 으로 추론 비활성화 → 응답 24초 → 약 4초
 * - responseSchema 로 3종 견적 JSON 출력을 강제 (할루시네이션 방지)
 * - Spring Boot 4 = Jackson 3(tools.jackson.databind)
 */
@Service
public class GeminiService {

    private final RestClient rest;
    private final ObjectMapper om = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiService(@Value("${gemini.base-url}") String baseUrl,
                         @Value("${gemini.api-key}") String apiKey,
                         @Value("${gemini.model}") String model) {
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public List<GeminiBuild> generateBuilds(String userPrompt) {
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text",
                        "너는 PC 견적 전문가다. 반드시 후보군 목록에 있는 part_id 만 사용하고, "
                        + "소켓/램규격/폼팩터/GPU길이/쿨러높이/파워 권장출력 호환성을 지켜라. 예산을 초과하지 마라."))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema(),
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );

        String raw = rest.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        GenResp resp = om.readValue(raw, GenResp.class);
        if (resp.candidates() == null || resp.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini 응답에 candidates 없음: " + raw);
        }
        String text = resp.candidates().get(0).content().parts().get(0).text();
        return om.readValue(text, BuildsWrapper.class).builds();
    }

    /** builds[] 각 항목이 tier + 8개 부품 part_id + total_price + reason 을 갖도록 스키마 정의 */
    private Map<String, Object> responseSchema() {
        Map<String, Object> intType = Map.of("type", "INTEGER");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("tier", Map.of("type", "STRING", "enum", List.of("가성비", "안정성", "최고성능")));
        for (String key : List.of("cpu_part_id", "gpu_part_id", "mainboard_part_id", "ram_part_id",
                "psu_part_id", "cooler_part_id", "storage_part_id", "case_part_id", "total_price")) {
            props.put(key, intType);
        }
        props.put("reason", Map.of("type", "STRING"));

        Map<String, Object> item = Map.of(
                "type", "OBJECT",
                "properties", props,
                "required", new ArrayList<>(props.keySet())
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
