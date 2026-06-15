package com.lina.bff.admin.dashboard.controller;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminFeedbackDashboardService;
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
 * 작성목적 : 관리자 대시보드 피드백 현황 API 컨트롤러.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 6 — GET /api/admin/feedback 추가
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
public class AdminFeedbackController {

  private final AdminAuthorizationService adminAuthorizationService;
  private final AdminDashboardQueryParser adminDashboardQueryParser;
  private final AdminFeedbackDashboardService adminFeedbackDashboardService;

  @GetMapping("/feedback")
  public ResponseEntity<ApiResponse<AdminFeedbackResponse>> getFeedback(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    adminAuthorizationService.requireAdmin();
    AdminDashboardQuery query = adminDashboardQueryParser.parse(period, from, to, page, size);
    return ResponseEntity.ok(
        ApiResponse.success(adminFeedbackDashboardService.getFeedback(query), "피드백 현황 조회 성공"));
  }
}
