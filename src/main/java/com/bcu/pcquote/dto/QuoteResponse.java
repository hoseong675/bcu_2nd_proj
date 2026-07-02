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
