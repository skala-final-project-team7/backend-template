package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.chat.entity.Conversation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 사용자 현황 API용 MongoDB 읽기 전용 repository.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — 사용자별 활성 대화 수 집계 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 * --------------------------------------------------
 * </pre>
 */
@Repository
public class AdminUserMongoRepository {

  private final MongoTemplate mongoTemplate;
  private static final int USER_ID_BATCH_SIZE = 500;

  public AdminUserMongoRepository(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public Map<String, Long> countActiveConversationsByUserIds(List<String> userIds) {
    if (userIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> deduplicatedUserIds =
        userIds.stream().distinct().collect(Collectors.toList());
    if (deduplicatedUserIds.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Long> conversationCounts = new HashMap<>();
    for (int offset = 0; offset < deduplicatedUserIds.size(); offset += USER_ID_BATCH_SIZE) {
      int end = Math.min(offset + USER_ID_BATCH_SIZE, deduplicatedUserIds.size());
      List<String> batch = deduplicatedUserIds.subList(offset, end);
      countActiveConversationsByUserIdsBatch(batch).forEach(
          (userId, count) -> conversationCounts.merge(userId, count, Long::sum));
    }
    return conversationCounts;
  }

  private Map<String, Long> countActiveConversationsByUserIdsBatch(List<String> userIds) {
    if (userIds.isEmpty()) {
      return Collections.emptyMap();
    }

    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(Criteria.where("deletedAt").is(null).and("userId").in(userIds)),
            Aggregation.group("userId").count().as("count"),
            Aggregation.project("count").and("_id").as("userId"));
    AggregationResults<UserConversationCount> results =
        mongoTemplate.aggregate(
            aggregation,
            mongoTemplate.getCollectionName(Conversation.class),
            UserConversationCount.class);

    return results.getMappedResults().stream()
        .collect(Collectors.toMap(UserConversationCount::userId, UserConversationCount::count));
  }

  private record UserConversationCount(String userId, long count) {}
}
