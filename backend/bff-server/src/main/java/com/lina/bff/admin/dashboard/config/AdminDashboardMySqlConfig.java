package com.lina.bff.admin.dashboard.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 MySQL read-only JDBC 경계. BFF 기본 datasource auto-config 와 분리한다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — 조건부 read-only DataSource/JdbcClient 구성
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / HikariCP
 * --------------------------------------------------
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(AdminDashboardMySqlProperties.class)
public class AdminDashboardMySqlConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(
      prefix = "lina.admin.dashboard.mysql",
      name = "enabled",
      havingValue = "true")
  DataSource adminDashboardDataSource(AdminDashboardMySqlProperties properties) {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(properties.jdbcUrl());
    dataSource.setUsername(properties.username());
    dataSource.setPassword(properties.password());
    dataSource.setMaximumPoolSize(properties.maximumPoolSize());
    dataSource.setReadOnly(true);
    dataSource.setPoolName("admin-dashboard-readonly");
    return dataSource;
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "lina.admin.dashboard.mysql",
      name = "enabled",
      havingValue = "true")
  JdbcClient adminDashboardJdbcClient(
      @Qualifier("adminDashboardDataSource") DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }
}
