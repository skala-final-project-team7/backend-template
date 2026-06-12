package com.lina.bff.admin.dashboard.controller;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminSyncResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminSyncService;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import com.lina.common.response.ApiResponse;
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
 * 작성목적 : 관리자 대시보드 동기화 이력 API 컨트롤러.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 7 — GET /api/admin/sync 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSyncController {

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminDashboardQueryParser adminDashboardQueryParser;
  private final AdminSyncService adminSyncService;

  public AdminSyncController(
      AdminAuthorizationService adminAuthorizationService,
      AdminDashboardQueryParser adminDashboardQueryParser,
      AdminSyncService adminSyncService) {
    this.adminAuthorizationService = adminAuthorizationService;
    this.adminDashboardQueryParser = adminDashboardQueryParser;
    this.adminSyncService = adminSyncService;
  }

  @GetMapping("/sync")
  public ApiResponse<AdminSyncResponse> getSyncHistory(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    adminAuthorizationService.requireAdmin();
    AdminDashboardQuery query = adminDashboardQueryParser.parse(null, from, to, page, size);
    return ApiResponse.success(adminSyncService.getSyncHistory(query), "동기화 이력 조회 성공");
  }
}
