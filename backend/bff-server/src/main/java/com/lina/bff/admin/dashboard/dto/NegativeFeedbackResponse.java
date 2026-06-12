package com.lina.bff.admin.dashboard.dto;

import java.time.ZonedDateTime;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 피드백 화면의 부정 피드백 원문(QCA) 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 6 — DISLIKE 피드백과 질문/답변 원문 매핑 응답 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record NegativeFeedbackResponse(
    String feedbackId,
    String messageId,
    String comment,
    String question,
    String answer,
    ZonedDateTime createdAt) {}
