package com.bcu.pcquote.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 견적 요청/설문 (정량+정성 요구사항) */
@Entity
@Table(name = "survey_requests")
@Getter
@Setter
@NoArgsConstructor
public class SurveyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id")
    private Long userId;

    private String purpose;                 // 게이밍/영상편집/사무/AI/기타

    @Column(name = "budget_min")
    private Integer budgetMin;

    @Column(name = "budget_max")
    private Integer budgetMax;

    @Column(name = "resolution_target")
    private String resolutionTarget;        // 1080p/1440p/4K

    @Column(name = "detail_requirement")
    private String detailRequirement;

    private String status = "요청";          // 요청/처리중/완료/실패

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
