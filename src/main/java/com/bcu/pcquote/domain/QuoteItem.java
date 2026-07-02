package com.bcu.pcquote.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 견적 구성 부품 (생성 시점 가격 스냅샷 박제) */
@Entity
@Table(name = "quote_items")
@Getter
@Setter
@NoArgsConstructor
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_item_id")
    private Long quoteItemId;

    @Column(name = "quote_id")
    private Long quoteId;

    @Column(name = "part_id")
    private Long partId;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "unit_price")
    private Integer unitPrice;
}
