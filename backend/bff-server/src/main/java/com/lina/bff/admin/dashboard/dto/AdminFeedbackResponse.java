package com.lina.bff.admin.dashboard.dto;

import java.util.List;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 피드백 현황 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 6 — 피드백 집계·추이·부정 QCA 목록 응답 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record AdminFeedbackResponse(
    long totalCount,
    long likeCount,
    long dislikeCount,
    double positiveRatio,
    List<AdminFeedbackTrendItemResponse> trend,
    List<NegativeFeedbackResponse> negativeFeedbacks,
    int page,
    int size) {}
