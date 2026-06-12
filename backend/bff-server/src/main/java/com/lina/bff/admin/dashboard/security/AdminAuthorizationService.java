package com.lina.bff.admin.dashboard.security;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 API 공통 ADMIN 권한 검사 경계.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 2 — /api/admin/* 대시보드 공통 권한 검사 서비스 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
public class AdminAuthorizationService {

  private static final String ADMIN_ROLE = "ADMIN";

  private final CurrentUserProvider currentUserProvider;

  public AdminAuthorizationService(CurrentUserProvider currentUserProvider) {
    this.currentUserProvider = currentUserProvider;
  }

  public void requireAdmin() {
    String userId = currentUserProvider.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
    }

    String role = currentUserProvider.getRole();
    if (role == null || !ADMIN_ROLE.equalsIgnoreCase(role.trim())) {
      throw new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
    }
  }
}
