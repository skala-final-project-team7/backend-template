package com.lina.bff.admin.dashboard.repository;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 데이터 현황 API용 RAG 파이프라인 MongoDB 읽기 전용 repository.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 5 — raw_pages/raw_attachments/chunked_units/sync_logs 집계 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 *   - MongoDB 7.x
 * --------------------------------------------------
 * </pre>
 */
@Repository
public class AdminDataMongoRepository {

  private static final String RAW_PAGES = "raw_pages";
  private static final String RAW_ATTACHMENTS = "raw_attachments";
  private static final String CHUNKED_UNITS = "chunked_units";
  private static final String SYNC_LOGS = "sync_logs";
  private static final List<String> SPACE_ID_FIELDS =
      List.of("spaceId", "space_id", "spaceKey", "space_key");
  private static final List<String> SYNC_TIME_FIELDS =
      List.of("completedAt", "completed_at", "updatedAt", "updated_at", "createdAt", "created_at");

  private final MongoTemplate mongoTemplate;

  public AdminDataMongoRepository(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public AdminDataSnapshot getSnapshot() {
    return new AdminDataSnapshot(
        countDistinctSpaces(),
        countDocuments(RAW_PAGES),
        countDocuments(RAW_ATTACHMENTS),
        countDocuments(CHUNKED_UNITS),
        findLastSyncAt());
  }

  private long countDocuments(String collectionName) {
    if (!mongoTemplate.collectionExists(collectionName)) {
      return 0L;
    }
    return mongoTemplate.count(new Query(), collectionName);
  }

  private long countDistinctSpaces() {
    if (!mongoTemplate.collectionExists(RAW_PAGES)) {
      return 0L;
    }

    return SPACE_ID_FIELDS.stream()
        .mapToLong(field -> countDistinctNonNull(RAW_PAGES, field))
        .filter(count -> count > 0)
        .findFirst()
        .orElse(0L);
  }

  private long countDistinctNonNull(String collectionName, String fieldName) {
    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(buildNonNullAndNonEmptyCriteria(fieldName)),
            Aggregation.group(fieldName),
            Aggregation.count().as("count"));
    AggregationResults<Document> result =
        mongoTemplate.aggregate(aggregation, collectionName, Document.class);
    Document count = result.getUniqueMappedResult();
    return count == null ? 0L : ((Number) count.get("count")).longValue();
  }

  private Instant findLastSyncAt() {
    if (!mongoTemplate.collectionExists(SYNC_LOGS)) {
      return null;
    }

    return SYNC_TIME_FIELDS.stream()
        .map(this::findLatestInstantByField)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private Instant findLatestInstantByField(String fieldName) {
    Query query =
        buildNonNullAndNonEmptyQuery(fieldName)
            .with(Sort.by(Sort.Direction.DESC, fieldName))
            .limit(1);
    Document document = mongoTemplate.findOne(query, Document.class, SYNC_LOGS);
    return document == null ? null : toInstant(document.get(fieldName));
  }

  private static Query buildNonNullAndNonEmptyQuery(String fieldName) {
    return Query.query(buildNonNullAndNonEmptyCriteria(fieldName));
  }

  private static Criteria buildNonNullAndNonEmptyCriteria(String fieldName) {
    return new Criteria()
        .andOperator(Criteria.where(fieldName).ne(null), Criteria.where(fieldName).ne(""));
  }

  private static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof Date date) {
      return date.toInstant();
    }
    if (value instanceof String text && !text.isBlank()) {
      return parseStringToInstant(text);
    }
    return null;
  }

  private static Instant parseStringToInstant(String value) {
    try {
      return Instant.parse(value);
    } catch (DateTimeException first) {
      return parseStringToInstantWithFallback(value);
    }
  }

  private static Instant parseStringToInstantWithFallback(String value) {
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeException second) {
      try {
        return ZonedDateTime.parse(value).toInstant();
      } catch (DateTimeException third) {
        return null;
      }
    }
  }
}
