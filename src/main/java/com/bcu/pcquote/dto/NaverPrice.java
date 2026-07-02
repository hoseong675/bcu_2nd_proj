package com.bcu.pcquote.dto;

/** 네이버 쇼핑 검색에서 뽑은 최저가 정보 */
public record NaverPrice(
        int price,
        String title,
        String productUrl,
        String mall
) {}
