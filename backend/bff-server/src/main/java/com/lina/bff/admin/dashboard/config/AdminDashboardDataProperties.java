package com.lina.bff.admin.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 데이터 현황 API 운영 설정.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 5 — vector DB 크기 표시값 설정 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@ConfigurationProperties(prefix = "lina.admin.dashboard.data")
public record AdminDashboardDataProperties(String vectorDbSize) {

  private static final String DEFAULT_VECTOR_DB_SIZE = "0 B";

  public AdminDashboardDataProperties {
    if (vectorDbSize == null || vectorDbSize.isBlank()) {
      vectorDbSize = DEFAULT_VECTOR_DB_SIZE;
    }
  }
}
