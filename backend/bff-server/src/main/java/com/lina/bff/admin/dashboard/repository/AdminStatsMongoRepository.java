package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
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
 * 작성목적 : 관리자 통계 API용 MongoDB 읽기 전용 repository.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 3 — conversations/messages 기반 통계 조회 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 *   - MongoDB 7.x
 * --------------------------------------------------
 * </pre>
 */
@Repository
@RequiredArgsConstructor
public class AdminStatsMongoRepository {

  private final MongoTemplate mongoTemplate;

  public long countActiveConversations() {
    Query query = Query.query(Criteria.where("deletedAt").is(null));
    return mongoTemplate.count(query, Conversation.class);
  }

  public List<Message> findActiveMessagesBetween(Instant fromInclusive, Instant toExclusive) {
    Query query =
        Query.query(
                Criteria.where("deletedAt")
                    .is(null)
                    .and("createdAt")
                    .gte(fromInclusive)
                    .lt(toExclusive))
            .with(Sort.by(Sort.Direction.ASC, "conversationId", "createdAt", "_id"));
    return mongoTemplate.find(query, Message.class);
  }
}
