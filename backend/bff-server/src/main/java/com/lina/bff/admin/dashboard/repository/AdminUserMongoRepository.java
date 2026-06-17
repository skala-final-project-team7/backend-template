package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.chat.entity.Conversation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
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
  private static final String RAW_PAGES = "raw_pages";
  // 빈 제약(공개) 페이지 ACL sentinel — 모든 인증 사용자 허용(ingestion allow_authenticated 정합).
  private static final String PUBLIC_ACL_GROUP = "*";

  public AdminUserMongoRepository(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * 사용자(userId + 소속 groupIds)의 ACL 로 접근 가능한 raw_pages 를 집계한다. 한 페이지에 접근
   * 가능 = allowed_groups 에 공개 sentinel("*") 또는 사용자 그룹이 있거나, allowed_users 에 userId 가
   * 있을 때. 스페이스 수는 접근 페이지의 distinct space_key, 첨부 수는 내장 attachments 배열 합.
   */
  public AccessibleCounts countAccessiblePages(String userId, List<String> groupIds) {
    if (!mongoTemplate.collectionExists(RAW_PAGES)) {
      return AccessibleCounts.ZERO;
    }

    List<Criteria> aclClauses = new ArrayList<>();
    aclClauses.add(Criteria.where("allowed_groups").is(PUBLIC_ACL_GROUP));
    if (groupIds != null && !groupIds.isEmpty()) {
      aclClauses.add(Criteria.where("allowed_groups").in(groupIds));
    }
    if (userId != null && !userId.isBlank()) {
      aclClauses.add(Criteria.where("allowed_users").is(userId));
    }

    Query query = new Query(new Criteria().orOperator(aclClauses.toArray(Criteria[]::new)));
    query.fields().include("space_key").include("attachments");
    List<Document> pages = mongoTemplate.find(query, Document.class, RAW_PAGES);

    Set<String> spaces = new HashSet<>();
    long attachmentCount = 0L;
    for (Document page : pages) {
      Object spaceKey = page.get("space_key");
      if (spaceKey != null) {
        spaces.add(spaceKey.toString());
      }
      if (page.get("attachments") instanceof List<?> attachments) {
        attachmentCount += attachments.size();
      }
    }
    return new AccessibleCounts(spaces.size(), pages.size(), attachmentCount);
  }

  /** 사용자별 접근 가능 스페이스/페이지/첨부 수. */
  public record AccessibleCounts(long spaceCount, long pageCount, long attachmentCount) {
    public static final AccessibleCounts ZERO = new AccessibleCounts(0L, 0L, 0L);
  }

  public Map<String, Long> countActiveConversationsByUserIds(List<String> userIds) {
    if (userIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> deduplicatedUserIds = userIds.stream().distinct().collect(Collectors.toList());
    if (deduplicatedUserIds.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Long> conversationCounts = new HashMap<>();
    for (int offset = 0; offset < deduplicatedUserIds.size(); offset += USER_ID_BATCH_SIZE) {
      int end = Math.min(offset + USER_ID_BATCH_SIZE, deduplicatedUserIds.size());
      List<String> batch = deduplicatedUserIds.subList(offset, end);
      countActiveConversationsByUserIdsBatch(batch)
          .forEach((userId, count) -> conversationCounts.merge(userId, count, Long::sum));
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
