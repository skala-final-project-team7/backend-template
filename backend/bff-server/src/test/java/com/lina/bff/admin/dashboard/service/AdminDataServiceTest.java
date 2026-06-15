package com.lina.bff.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.dashboard.config.AdminDashboardDataProperties;
import com.lina.bff.admin.dashboard.dto.AdminDataResponse;
import com.lina.bff.admin.dashboard.repository.AdminDataMongoRepository;
import com.lina.bff.admin.dashboard.repository.AdminDataSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDataServiceTest {

  @Mock private AdminDataMongoRepository adminDataMongoRepository;

  @Test
  @DisplayName("Mongo 집계값과 설정 기반 vector DB 크기를 데이터 현황 응답으로 변환한다")
  void shouldReturnDataSnapshot() {
    when(adminDataMongoRepository.getSnapshot())
        .thenReturn(
            new AdminDataSnapshot(5L, 1230L, 187L, 8940L, Instant.parse("2026-05-20T08:00:00Z")));

    AdminDataResponse response =
        new AdminDataService(adminDataMongoRepository, new AdminDashboardDataProperties("2.3 GB"))
            .getData();

    assertThat(response.totalSpaces()).isEqualTo(5);
    assertThat(response.totalPages()).isEqualTo(1230);
    assertThat(response.totalAttachments()).isEqualTo(187);
    assertThat(response.vectorDbSize()).isEqualTo("2.3 GB");
    assertThat(response.totalChunks()).isEqualTo(8940);
    assertThat(response.lastSyncAt().toOffsetDateTime().toString())
        .isEqualTo("2026-05-20T17:00+09:00");
  }

  @Test
  @DisplayName("수집 데이터가 없으면 0 집계와 lastSyncAt=null 을 반환한다")
  void shouldReturnZeroDataSnapshot() {
    when(adminDataMongoRepository.getSnapshot())
        .thenReturn(new AdminDataSnapshot(0L, 0L, 0L, 0L, null));

    AdminDataResponse response =
        new AdminDataService(adminDataMongoRepository, new AdminDashboardDataProperties(null))
            .getData();

    assertThat(response.totalSpaces()).isZero();
    assertThat(response.totalPages()).isZero();
    assertThat(response.totalAttachments()).isZero();
    assertThat(response.vectorDbSize()).isEqualTo("0 B");
    assertThat(response.totalChunks()).isZero();
    assertThat(response.lastSyncAt()).isNull();
  }
}
