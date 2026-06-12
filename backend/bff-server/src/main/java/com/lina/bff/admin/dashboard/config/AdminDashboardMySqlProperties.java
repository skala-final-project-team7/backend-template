package com.lina.bff.admin.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드용 auth-server MySQL read-only datasource 설정.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — users/user_groups 읽기 전용 datasource 설정 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@ConfigurationProperties(prefix = "lina.admin.dashboard.mysql")
public record AdminDashboardMySqlProperties(
    boolean enabled, String jdbcUrl, String username, String password, int maximumPoolSize) {

  private static final int DEFAULT_MAXIMUM_POOL_SIZE = 5;

  public AdminDashboardMySqlProperties {
    maximumPoolSize = maximumPoolSize <= 0 ? DEFAULT_MAXIMUM_POOL_SIZE : maximumPoolSize;
  }
}
