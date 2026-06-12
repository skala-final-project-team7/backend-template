package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 동기화 이력 API용 RAG 파이프라인 MongoDB 읽기 전용 repository.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 7 — sync_logs 기간/페이지 조회 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 *   - MongoDB 7.x
 * --------------------------------------------------
 * </pre>
 */
@Repository
public class AdminSyncMongoRepository {

  private static final String SYNC_LOGS = "sync_logs";
  private static final List<String> COMPLETED_TIME_FIELDS =
      List.of(
          "completedAt", "completed_at", "finishedAt", "finished_at", "updatedAt", "updated_at");

  private final MongoTemplate mongoTemplate;

  public AdminSyncMongoRepository(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public List<Document> findSyncLogsBetween(
      Instant fromInclusive, Instant toExclusive, AdminDashboardPageRequest pageRequest) {
    if (!mongoTemplate.collectionExists(SYNC_LOGS)) {
      return List.of();
    }

    Query query =
        Query.query(timeRangeCriteria(fromInclusive, toExclusive))
            .with(
                Sort.by(
                    Sort.Order.desc("completedAt"),
                    Sort.Order.desc("completed_at"),
                    Sort.Order.desc("finishedAt"),
                    Sort.Order.desc("finished_at"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("updated_at"),
                    Sort.Order.desc("_id")))
            .skip((long) pageRequest.page() * pageRequest.size())
            .limit(pageRequest.size());
    return mongoTemplate.find(query, Document.class, SYNC_LOGS);
  }

  private Criteria timeRangeCriteria(Instant fromInclusive, Instant toExclusive) {
    Criteria[] criteria =
        COMPLETED_TIME_FIELDS.stream()
            .map(fieldName -> Criteria.where(fieldName).gte(fromInclusive).lt(toExclusive))
            .toArray(Criteria[]::new);
    return new Criteria().orOperator(criteria);
  }
}
