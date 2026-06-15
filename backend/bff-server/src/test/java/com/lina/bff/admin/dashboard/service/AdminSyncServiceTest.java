package com.lina.bff.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminDashboardTimeRange;
import com.lina.bff.admin.dashboard.dto.AdminSyncResponse;
import com.lina.bff.admin.dashboard.repository.AdminSyncMongoRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSyncServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock private AdminSyncMongoRepository adminSyncMongoRepository;

  @Test
  @DisplayName("sync_logs 문서를 API 응답 필드로 변환하고 duration 은 초 단위로 반환한다")
  void shouldMapSyncLogsToResponse() {
    AdminDashboardQuery query =
        query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00", 0, 20);
    Document completed =
        new Document("syncId", "sync-001")
            .append("status", "SUCCESS")
            .append("updatedPages", 12)
            .append("deletedPages", 1)
            .append("durationMillis", 45_200)
            .append("completedAt", Date.from(Instant.parse("2026-06-10T08:00:00Z")));
    Document failed =
        new Document("job_id", "job-002")
            .append("status", "ERROR")
            .append("processed_pages", 3)
            .append("deleted_pages", 0)
            .append("started_at", Date.from(Instant.parse("2026-06-10T08:00:00Z")))
            .append("finished_at", Date.from(Instant.parse("2026-06-10T08:00:09Z")));
    when(adminSyncMongoRepository.findSyncLogsBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of(completed, failed));

    AdminSyncResponse response =
        new AdminSyncService(adminSyncMongoRepository).getSyncHistory(query);

    assertThat(response.syncHistory()).hasSize(2);
    assertThat(response.syncHistory().get(0).syncId()).isEqualTo("sync-001");
    assertThat(response.syncHistory().get(0).status()).isEqualTo("COMPLETED");
    assertThat(response.syncHistory().get(0).updatedPages()).isEqualTo(12);
    assertThat(response.syncHistory().get(0).deletedPages()).isEqualTo(1);
    assertThat(response.syncHistory().get(0).duration()).isEqualTo(45);
    assertThat(response.syncHistory().get(0).completedAt().toOffsetDateTime().toString())
        .isEqualTo("2026-06-10T17:00+09:00");
    assertThat(response.syncHistory().get(1).syncId()).isEqualTo("job-002");
    assertThat(response.syncHistory().get(1).status()).isEqualTo("FAILED");
    assertThat(response.syncHistory().get(1).updatedPages()).isEqualTo(3);
    assertThat(response.syncHistory().get(1).duration()).isEqualTo(9);
  }

  @Test
  @DisplayName("syncId 후보가 없으면 Mongo _id 를 fallback 으로 사용하고 빈 목록도 허용한다")
  void shouldUseMongoIdFallbackAndReturnEmptyList() {
    AdminDashboardQuery query =
        query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00", 1, 10);
    ObjectId objectId = new ObjectId("66554433221100ffeeddccbb");
    Document document =
        new Document("_id", objectId)
            .append("status", "IN_PROGRESS")
            .append("updated_pages", "7")
            .append("deleted_pages", "2")
            .append("duration_seconds", "11")
            .append("updated_at", "2026-06-10T12:00:00+09:00");
    when(adminSyncMongoRepository.findSyncLogsBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of(document));

    AdminSyncResponse response =
        new AdminSyncService(adminSyncMongoRepository).getSyncHistory(query);

    assertThat(response.syncHistory()).hasSize(1);
    assertThat(response.syncHistory().get(0).syncId()).isEqualTo("66554433221100ffeeddccbb");
    assertThat(response.syncHistory().get(0).status()).isEqualTo("IN_PROGRESS");
    assertThat(response.syncHistory().get(0).updatedPages()).isEqualTo(7);
    assertThat(response.syncHistory().get(0).deletedPages()).isEqualTo(2);
    assertThat(response.syncHistory().get(0).duration()).isEqualTo(11);
  }

  @Test
  @DisplayName("조회 결과가 없으면 syncHistory 빈 배열을 반환한다")
  void shouldReturnEmptySyncHistory() {
    AdminDashboardQuery query =
        query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00", 0, 20);
    when(adminSyncMongoRepository.findSyncLogsBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of());

    AdminSyncResponse response =
        new AdminSyncService(adminSyncMongoRepository).getSyncHistory(query);

    assertThat(response.syncHistory()).isEmpty();
  }

  private static AdminDashboardQuery query(String fromKst, String toKst, int page, int size) {
    ZonedDateTime from = ZonedDateTime.parse(fromKst).withZoneSameInstant(KST);
    ZonedDateTime to = ZonedDateTime.parse(toKst).withZoneSameInstant(KST);
    return new AdminDashboardQuery(
        AdminDashboardPeriod.DAILY,
        new AdminDashboardTimeRange(from, to, from.toInstant(), to.toInstant()),
        new AdminDashboardPageRequest(page, size));
  }
}
