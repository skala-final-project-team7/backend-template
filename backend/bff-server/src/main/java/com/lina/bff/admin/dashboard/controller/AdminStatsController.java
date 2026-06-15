package com.lina.bff.admin.dashboard.controller;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminStatsService;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import com.lina.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 사용 통계 API 컨트롤러.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 3 — GET /api/admin/stats 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminDashboardQueryParser adminDashboardQueryParser;
  private final AdminStatsService adminStatsService;

  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    adminAuthorizationService.requireAdmin();
    AdminDashboardQuery query = adminDashboardQueryParser.parse(period, from, to, null, null);
    return ResponseEntity.ok(
        ApiResponse.success(adminStatsService.getStats(query), "서비스 통계 조회 성공"));
  }
}
