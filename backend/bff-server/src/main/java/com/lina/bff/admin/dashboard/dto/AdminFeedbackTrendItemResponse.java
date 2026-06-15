package com.lina.bff.admin.dashboard.dto;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 피드백 추이 버킷 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 6 — KST 일/시 단위 LIKE/DISLIKE 추이 응답 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record AdminFeedbackTrendItemResponse(String date, long likeCount, long dislikeCount) {}
