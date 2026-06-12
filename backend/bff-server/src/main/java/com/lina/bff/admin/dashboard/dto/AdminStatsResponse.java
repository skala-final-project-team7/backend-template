package com.lina.bff.admin.dashboard.dto;

import java.util.List;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 사용 통계 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 3 — /api/admin/stats 응답 모델 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
public record AdminStatsResponse(
    long dailyQueryCount,
    double avgResponseTime,
    long totalConversations,
    List<HourlyAccessTrendItem> hourlyAccessTrend) {

  public AdminStatsResponse {
    hourlyAccessTrend = List.copyOf(hourlyAccessTrend);
  }

  /** KST 시간대별 사용자 질문 수. */
  public record HourlyAccessTrendItem(int hour, long count) {}
}
