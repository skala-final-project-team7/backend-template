package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.feedback.entity.Feedback;
import com.lina.bff.feedback.entity.FeedbackRating;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
 * 작성목적 : 관리자 피드백 현황 API용 MongoDB 읽기 전용 repository.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 6 — feedbacks/messages 기반 피드백 집계 및 QCA 조회 추가
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
public class AdminFeedbackRepository {

  private final MongoTemplate mongoTemplate;

  public List<Feedback> findFeedbacksBetween(Instant fromInclusive, Instant toExclusive) {
    Query query =
        Query.query(Criteria.where("createdAt").gte(fromInclusive).lt(toExclusive))
            .with(Sort.by(Sort.Direction.ASC, "createdAt", "_id"));
    return mongoTemplate.find(query, Feedback.class);
  }

  public List<Feedback> findNegativeFeedbacksBetween(
      Instant fromInclusive, Instant toExclusive, AdminDashboardPageRequest pageRequest) {
    Query query =
        Query.query(
                Criteria.where("createdAt")
                    .gte(fromInclusive)
                    .lt(toExclusive)
                    .and("rating")
                    .is(FeedbackRating.DISLIKE))
            .with(Sort.by(Sort.Direction.DESC, "createdAt", "_id"))
            .skip((long) pageRequest.page() * pageRequest.size())
            .limit(pageRequest.size());
    return mongoTemplate.find(query, Feedback.class);
  }

  public Map<String, Message> findActiveMessagesByIds(Collection<String> messageIds) {
    if (messageIds == null || messageIds.isEmpty()) {
      return Collections.emptyMap();
    }

    Query query = Query.query(Criteria.where("_id").in(messageIds).and("deletedAt").is(null));
    return mongoTemplate.find(query, Message.class).stream()
        .collect(Collectors.toMap(Message::getMessageId, Function.identity()));
  }

  public List<Message> findActiveMessagesByConversationIds(Collection<String> conversationIds) {
    if (conversationIds == null || conversationIds.isEmpty()) {
      return List.of();
    }

    Query query =
        Query.query(Criteria.where("conversationId").in(conversationIds).and("deletedAt").is(null))
            .with(Sort.by(Sort.Direction.ASC, "conversationId", "createdAt", "_id"));
    return mongoTemplate.find(query, Message.class);
  }
}
