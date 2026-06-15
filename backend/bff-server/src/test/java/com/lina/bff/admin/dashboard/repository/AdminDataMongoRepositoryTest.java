package com.lina.bff.admin.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class AdminDataMongoRepositoryTest {

  @Mock private MongoTemplate mongoTemplate;

  @Test
  @DisplayName("RAG 파이프라인 컬렉션을 읽기 전용으로 집계한다")
  void shouldReadDataCollectionsOnly() {
    when(mongoTemplate.collectionExists("raw_pages")).thenReturn(true);
    when(mongoTemplate.collectionExists("raw_attachments")).thenReturn(true);
    when(mongoTemplate.collectionExists("chunked_units")).thenReturn(true);
    when(mongoTemplate.collectionExists("sync_logs")).thenReturn(true);
    when(mongoTemplate.count(any(Query.class), eq("raw_pages"))).thenReturn(1230L);
    when(mongoTemplate.count(any(Query.class), eq("raw_attachments"))).thenReturn(187L);
    when(mongoTemplate.count(any(Query.class), eq("chunked_units"))).thenReturn(8940L);
    when(mongoTemplate.findDistinct(
            any(Query.class), eq("spaceId"), eq("raw_pages"), eq(String.class)))
        .thenReturn(List.of("SPACE-A", "SPACE-B", "SPACE-C", "SPACE-D", "SPACE-E"));
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("sync_logs")))
        .thenReturn(new Document("completedAt", Date.from(Instant.parse("2026-05-20T08:00:00Z"))));

    AdminDataSnapshot snapshot = new AdminDataMongoRepository(mongoTemplate).getSnapshot();

    assertThat(snapshot.totalSpaces()).isEqualTo(5);
    assertThat(snapshot.totalPages()).isEqualTo(1230);
    assertThat(snapshot.totalAttachments()).isEqualTo(187);
    assertThat(snapshot.totalChunks()).isEqualTo(8940);
    assertThat(snapshot.lastSyncAt()).isEqualTo(Instant.parse("2026-05-20T08:00:00Z"));
    verify(mongoTemplate, never()).save(any(Object.class));
    verify(mongoTemplate, never()).remove(any(Query.class), any(String.class));
  }

  @Test
  @DisplayName("컬렉션이 없으면 0 집계와 lastSyncAt=null 을 반환한다")
  void shouldReturnZeroWhenCollectionsAreMissing() {
    when(mongoTemplate.collectionExists("raw_pages")).thenReturn(false);
    when(mongoTemplate.collectionExists("raw_attachments")).thenReturn(false);
    when(mongoTemplate.collectionExists("chunked_units")).thenReturn(false);
    when(mongoTemplate.collectionExists("sync_logs")).thenReturn(false);

    AdminDataSnapshot snapshot = new AdminDataMongoRepository(mongoTemplate).getSnapshot();

    assertThat(snapshot.totalSpaces()).isZero();
    assertThat(snapshot.totalPages()).isZero();
    assertThat(snapshot.totalAttachments()).isZero();
    assertThat(snapshot.totalChunks()).isZero();
    assertThat(snapshot.lastSyncAt()).isNull();
  }

  @Test
  @DisplayName("동기화 시각이 ISO 형식이 아니더라도 500 대신 안전하게 null 또는 다음 필드로 대체한다")
  void shouldIgnoreInvalidSyncTimeValue() {
    when(mongoTemplate.collectionExists("raw_pages")).thenReturn(false);
    when(mongoTemplate.collectionExists("raw_attachments")).thenReturn(false);
    when(mongoTemplate.collectionExists("chunked_units")).thenReturn(false);
    when(mongoTemplate.collectionExists("sync_logs")).thenReturn(true);

    var findOneCallCount = new AtomicInteger(0);
    when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("sync_logs")))
        .thenAnswer(
            invocation -> {
              if (findOneCallCount.getAndIncrement() == 0) {
                return new Document("completedAt", "invalid-timestamp");
              }
              return new Document("completed_at", "2026-06-10T00:00:00Z");
            });

    AdminDataSnapshot snapshot = new AdminDataMongoRepository(mongoTemplate).getSnapshot();

    assertThat(snapshot.lastSyncAt()).isEqualTo(Instant.parse("2026-06-10T00:00:00Z"));
    assertThat(snapshot.totalSpaces()).isZero();
    assertThat(snapshot.totalPages()).isZero();
  }
}
