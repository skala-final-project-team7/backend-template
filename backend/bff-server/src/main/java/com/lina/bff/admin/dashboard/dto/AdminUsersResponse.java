package com.lina.bff.admin.dashboard.dto;

import java.util.List;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 사용자 현황 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — /api/admin/users 응답 모델 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
public record AdminUsersResponse(
    long totalUsers,
    long dailyActiveUsers,
    int page,
    int size,
    List<AdminUserSummaryResponse> users) {

  public AdminUsersResponse {
    users = List.copyOf(users);
  }
}
