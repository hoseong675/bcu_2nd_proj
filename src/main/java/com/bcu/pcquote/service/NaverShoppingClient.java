package com.bcu.pcquote.service;

import com.bcu.pcquote.dto.NaverPrice;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 네이버 쇼핑 검색 API 클라이언트 (readme 2.2단계 가격 캐싱).
 * 부품명으로 검색해 최저가(lprice) 상품을 반환한다.
 */
@Service
public class NaverShoppingClient {

    private final RestClient rest;
    private final ObjectMapper om = new ObjectMapper();

    public NaverShoppingClient(@Value("${naver.base-url}") String baseUrl,
                               @Value("${naver.client-id}") String clientId,
                               @Value("${naver.client-secret}") String clientSecret) {
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Naver-Client-Id", clientId)
                .defaultHeader("X-Naver-Client-Secret", clientSecret)
                .build();
    }

    /**
     * 부품명으로 검색해 대표 가격을 찾는다. 없으면 empty.
     * 전략: 관련도순(sim) 상위 3개 중 최저가.
     *   - sim 상위는 실제 해당 제품군이라 짝퉁/악세서리 최저가 노이즈를 피함
     *   - 그 중 최저가를 취해 단일 고가 이상치를 완화
     * (한계: 이름 기반 퍼지 매칭이라 변형모델/번들이 섞일 수 있음.
     *  운영 단계에서는 부품별 상품ID를 매핑하는 방식이 정확함.)
     */
    public Optional<NaverPrice> findPrice(String query) {
        String raw = rest.get()
                .uri(uri -> uri.path("/v1/search/shop.json")
                        .queryParam("query", query)
                        .queryParam("display", 10)
                        .queryParam("sort", "sim")   // 관련도순
                        .build())
                .retrieve()
                .body(String.class);

        ShopResponse resp = om.readValue(raw, ShopResponse.class);
        if (resp.items() == null) {
            return Optional.empty();
        }
        return resp.items().stream()
                .map(Item::toPrice)
                .filter(p -> p != null && p.price() > 0)
                .limit(3)                                       // 관련도 상위 3개
                .min(Comparator.comparingInt(NaverPrice::price));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShopResponse(Integer total, List<Item> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String title, String lprice, String link, String mallName) {
        NaverPrice toPrice() {
            try {
                int price = Integer.parseInt(lprice);
                String cleanTitle = title == null ? "" : title.replaceAll("<.*?>", "");
                return new NaverPrice(price, cleanTitle, link, mallName);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
