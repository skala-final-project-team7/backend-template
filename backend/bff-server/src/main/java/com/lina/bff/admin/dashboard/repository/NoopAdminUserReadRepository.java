package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : MySQL read datasource 미설정 시 로컬 실행을 유지하는 관리자 사용자 현황 fallback.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — MySQL 비활성 환경용 빈 결과 repository 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Repository
@ConditionalOnProperty(
    prefix = "lina.admin.dashboard.mysql",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopAdminUserReadRepository implements AdminUserReadRepository {

  @Override
  public AdminUserPage findUsers(
      AdminDashboardPageRequest pageRequest, Instant fromUtc, Instant toUtc) {
    return new AdminUserPage(0L, 0L, List.of());
  }
}
