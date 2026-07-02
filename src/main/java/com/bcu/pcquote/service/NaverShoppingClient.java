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
     * 전략: 관련도순(sim) 상위 5개에서 극단 저가(악세서리 "~용" 등) 제거 후 최저가.
     *   1) sim 상위는 실제 해당 제품군 → 짝퉁/무관 최저가 배제
     *   2) 상위 5개 중앙값의 30% 미만은 악세서리로 보고 제외
     *   3) 남은 것 중 최저가
     * (한계: 이름 기반 퍼지 매칭이라 변형모델/번들이 섞일 수 있음.
     *  정확도가 필요하면 parts.naver_query 로 부품별 정밀 쿼리를 지정하거나
     *  상품ID 매핑을 사용해야 함.)
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
        List<NaverPrice> top = resp.items().stream()
                .map(Item::toPrice)
                .filter(p -> p != null && p.price() > 0)
                .limit(5)                                       // 관련도 상위 5개
                .toList();
        if (top.isEmpty()) {
            return Optional.empty();
        }
        double median = median(top.stream().map(NaverPrice::price).sorted().toList());
        double floor = median * 0.3;                            // 극단 저가(악세서리) 컷
        return top.stream()
                .filter(p -> p.price() >= floor)
                .min(Comparator.comparingInt(NaverPrice::price));
    }

    private double median(List<Integer> sorted) {
        int n = sorted.size();
        return (n % 2 == 1) ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
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
