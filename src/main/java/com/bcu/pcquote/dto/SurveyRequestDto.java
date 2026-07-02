package com.bcu.pcquote.dto;

/** 견적 요청 입력 DTO (설문 결과) */
public record SurveyRequestDto(
        Long userId,
        String purpose,
        Integer budgetMin,
        Integer budgetMax,
        String resolutionTarget,
        String detailRequirement,
        Boolean integratedGraphicsOnly   // true 면 외장 GPU 없이 CPU 내장그래픽으로만 구성
) {}
