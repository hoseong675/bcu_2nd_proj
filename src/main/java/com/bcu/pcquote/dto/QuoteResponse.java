package com.bcu.pcquote.dto;

import java.util.List;

/** 견적 API 최종 응답 (part_id → 이름/가격으로 풀어서 반환) */
public record QuoteResponse(
        Long requestId,
        List<BuildView> builds
) {
    public record BuildView(
            String tier,
            Integer totalPrice,          // 캐싱 실가격으로 재계산한 실제 합계 (LLM 추정값 아님)
            boolean withinBudget,        // 예산 후처리: 실제 합계 <= 예산 상한
            boolean compatible,          // 2차 가드레일: 조합 호환성 재검증 결과
            List<String> warnings,       // 호환성 위반 목록 (compatible=false 일 때)
            String reason,
            List<ItemView> items
    ) {}

    public record ItemView(
            String category,
            Long partId,
            String name,
            Integer price
    ) {}
}
