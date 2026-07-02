package com.bcu.pcquote.web;

import com.bcu.pcquote.dto.QuoteResponse;
import com.bcu.pcquote.dto.SurveyRequestDto;
import com.bcu.pcquote.service.QuoteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 견적 요청 API (readme 파이프라인 진입점) */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** POST /api/quotes  →  설문 입력을 받아 3종 견적 반환 */
    @PostMapping
    public QuoteResponse create(@RequestBody SurveyRequestDto dto) {
        return quoteService.recommend(dto);
    }
}
