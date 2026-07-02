package com.bcu.pcquote.dto;

/** 호환성 필터링을 통과한 후보 부품 (Gemini 에 전달) */
public record CandidatePart(
        String category,        // CPU/GPU/MAINBOARD/RAM/PSU/COOLER/STORAGE/CASE
        Long partId,
        String manufacturer,
        String modelName,
        String spec,            // 호환성 관련 규격 요약(소켓/램규격/폼팩터/치수/전력 등)
        Integer price
) {}
