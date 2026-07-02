package com.bcu.pcquote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gemini 가 responseSchema 로 반환하는 견적 1종.
 * 부품은 후보군 part_id 를 STRING enum 으로 제약해 반환되므로 문자열로 받는다.
 */
public record GeminiBuild(
        String tier,
        @JsonProperty("cpu_part_id") String cpuPartId,
        @JsonProperty("gpu_part_id") String gpuPartId,
        @JsonProperty("mainboard_part_id") String mainboardPartId,
        @JsonProperty("ram_part_id") String ramPartId,
        @JsonProperty("psu_part_id") String psuPartId,
        @JsonProperty("cooler_part_id") String coolerPartId,
        @JsonProperty("storage_part_id") String storagePartId,
        @JsonProperty("case_part_id") String casePartId,
        @JsonProperty("total_price") Integer totalPrice,
        String reason
) {}
