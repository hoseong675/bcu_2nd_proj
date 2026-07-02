package com.bcu.pcquote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Gemini 가 responseSchema 로 반환하는 견적 1종 (부품은 part_id 로 지정) */
public record GeminiBuild(
        String tier,
        @JsonProperty("cpu_part_id") Long cpuPartId,
        @JsonProperty("gpu_part_id") Long gpuPartId,
        @JsonProperty("mainboard_part_id") Long mainboardPartId,
        @JsonProperty("ram_part_id") Long ramPartId,
        @JsonProperty("psu_part_id") Long psuPartId,
        @JsonProperty("cooler_part_id") Long coolerPartId,
        @JsonProperty("storage_part_id") Long storagePartId,
        @JsonProperty("case_part_id") Long casePartId,
        @JsonProperty("total_price") Integer totalPrice,
        String reason
) {}
