package com.lina.bff.admin.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class AdminSyncMongoRepositoryTest {

  @Mock private MongoTemplate mongoTemplate;

  @Test
  @DisplayName("sync_logs 컬렉션을 기간/페이지 조건으로 읽기 전용 조회한다")
  void shouldFindSyncLogsReadOnly() {
    AdminDashboardPageRequest pageRequest = new AdminDashboardPageRequest(2, 10);
    List<Document> documents = List.of(new Document("syncId", "sync-001"));
    when(mongoTemplate.collectionExists("sync_logs")).thenReturn(true);
    when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("sync_logs")))
        .thenReturn(documents);

    List<Document> result =
        new AdminSyncMongoRepository(mongoTemplate)
            .findSyncLogsBetween(
                Instant.parse("2026-06-09T15:00:00Z"),
                Instant.parse("2026-06-10T15:00:00Z"),
                pageRequest);

    assertThat(result).isEqualTo(documents);
    verify(mongoTemplate, never()).save(any(Object.class));
    verify(mongoTemplate, never()).remove(any(Query.class), any(String.class));
  }

  @Test
  @DisplayName("sync_logs 컬렉션이 없으면 Mongo find 없이 빈 목록을 반환한다")
  void shouldReturnEmptyWhenCollectionMissing() {
    when(mongoTemplate.collectionExists("sync_logs")).thenReturn(false);

    List<Document> result =
        new AdminSyncMongoRepository(mongoTemplate)
            .findSyncLogsBetween(
                Instant.parse("2026-06-09T15:00:00Z"),
                Instant.parse("2026-06-10T15:00:00Z"),
                new AdminDashboardPageRequest(0, 20));

    assertThat(result).isEmpty();
    verify(mongoTemplate, never()).find(any(Query.class), eq(Document.class), eq("sync_logs"));
  }
}
