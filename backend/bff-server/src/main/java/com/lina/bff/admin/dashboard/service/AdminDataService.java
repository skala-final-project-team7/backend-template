package com.lina.bff.admin.dashboard.service;

import com.lina.bff.admin.dashboard.config.AdminDashboardDataProperties;
import com.lina.bff.admin.dashboard.dto.AdminDataResponse;
import com.lina.bff.admin.dashboard.repository.AdminDataMongoRepository;
import com.lina.bff.admin.dashboard.repository.AdminDataSnapshot;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 데이터 현황 집계 서비스.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 5 — Confluence 수집 데이터 및 chunk 통계 집계
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
public class AdminDataService {

  private final AdminDataMongoRepository adminDataMongoRepository;
  private final AdminDashboardDataProperties adminDashboardDataProperties;

  public AdminDataService(
      AdminDataMongoRepository adminDataMongoRepository,
      AdminDashboardDataProperties adminDashboardDataProperties) {
    this.adminDataMongoRepository = adminDataMongoRepository;
    this.adminDashboardDataProperties = adminDashboardDataProperties;
  }

  public AdminDataResponse getData() {
    AdminDataSnapshot snapshot = adminDataMongoRepository.getSnapshot();
    return new AdminDataResponse(
        snapshot.totalSpaces(),
        snapshot.totalPages(),
        snapshot.totalAttachments(),
        adminDashboardDataProperties.vectorDbSize(),
        snapshot.totalChunks(),
        toKst(snapshot.lastSyncAt()));
  }

  private static ZonedDateTime toKst(java.time.Instant instant) {
    return instant == null ? null : instant.atZone(AdminDashboardQueryParser.KST);
  }
}
