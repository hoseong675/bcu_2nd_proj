package com.bcu.pcquote.service;

import com.bcu.pcquote.dto.NaverPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 가격 캐싱 배치 (readme 2.2단계).
 * 활성 부품을 순회하며 네이버 쇼핑 최저가를 조회해 part_prices 에 upsert 한다.
 * - 수동 트리거: POST /api/admin/price-batch
 * - 자동: 매일 새벽(cron) 스케줄
 */
@Service
public class PriceBatchService {

    private static final Logger log = LoggerFactory.getLogger(PriceBatchService.class);

    private static final String UPSERT = """
            INSERT INTO part_prices (part_id, source, price, product_url, is_lowest, snapshot_date, updated_at)
            VALUES (?, '네이버', ?, ?, 1, CURDATE(), NOW())
            ON DUPLICATE KEY UPDATE price = VALUES(price), product_url = VALUES(product_url),
                                    is_lowest = 1, updated_at = NOW()
            """;

    private final JdbcTemplate jdbc;
    private final NaverShoppingClient naver;

    public PriceBatchService(JdbcTemplate jdbc, NaverShoppingClient naver) {
        this.jdbc = jdbc;
        this.naver = naver;
    }

    public BatchResult run() {
        List<Map<String, Object>> parts =
                jdbc.queryForList("SELECT part_id, model_name FROM parts WHERE is_active = 1");

        int updated = 0;
        int failed = 0;
        for (Map<String, Object> p : parts) {
            Long partId = ((Number) p.get("part_id")).longValue();
            String name = (String) p.get("model_name");
            try {
                Optional<NaverPrice> price = naver.findPrice(name);
                if (price.isPresent()) {
                    jdbc.update(UPSERT, partId, price.get().price(), price.get().productUrl());
                    updated++;
                } else {
                    log.warn("검색 결과 없음: part {} '{}'", partId, name);
                    failed++;
                }
            } catch (Exception e) {
                log.warn("가격 조회 실패: part {} '{}' - {}", partId, name, e.toString());
                failed++;
            }
            sleep(120); // 네이버 API 부하 방지
        }
        log.info("가격 배치 완료: 전체 {} / 갱신 {} / 실패 {}", parts.size(), updated, failed);
        return new BatchResult(parts.size(), updated, failed);
    }

    /** 새벽 배치 (cron 은 application.yml 의 naver.batch-cron) */
    @Scheduled(cron = "${naver.batch-cron}")
    public void scheduledRun() {
        log.info("스케줄 가격 배치 시작");
        run();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record BatchResult(int total, int updated, int failed) {}
}
