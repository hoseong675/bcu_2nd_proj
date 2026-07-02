package com.bcu.pcquote.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 생성된 견적 (요청당 3종: 가성비/안정성/최고성능) */
@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_id")
    private Long quoteId;

    @Column(name = "request_id")
    private Long requestId;

    private String tier;                    // 가성비/안정성/최고성능

    @Column(name = "total_price")
    private Integer totalPrice;

    @Column(columnDefinition = "TEXT")
    private String reason;                   // AI 자연어 추천 사유

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
