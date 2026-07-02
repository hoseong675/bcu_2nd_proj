package com.bcu.pcquote.dto;

import java.util.List;

/** 견적 API 최종 응답 (part_id → 이름/가격으로 풀어서 반환) */
public record QuoteResponse(
        Long requestId,
        List<BuildView> builds
) {
    public record BuildView(
            String tier,
            Integer totalPrice,
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
