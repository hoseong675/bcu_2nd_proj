package com.bcu.pcquote.web;

import com.bcu.pcquote.service.PriceBatchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영용 엔드포인트 (가격 배치 수동 트리거) */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PriceBatchService priceBatchService;

    public AdminController(PriceBatchService priceBatchService) {
        this.priceBatchService = priceBatchService;
    }

    /** POST /api/admin/price-batch → 네이버 최저가로 part_prices 갱신 */
    @PostMapping("/price-batch")
    public PriceBatchService.BatchResult runPriceBatch() {
        return priceBatchService.run();
    }
}
