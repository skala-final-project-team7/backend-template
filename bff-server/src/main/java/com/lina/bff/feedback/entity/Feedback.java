package com.lina.bff.feedback.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 답변(assistant 메시지) 단위 피드백 문서. 메시지당 1건만 허용(unique index on messageId).
 *           재등록은 동일 문서 upsert 로 처리한다(신규/갱신 분기는 Feature 6 Service 책임).
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — feedbacks 테이블 매핑(MySQL/JPA)
 *   - 2026-05-20, 데이터 저장소 변경, JPA @Entity → MongoDB @Document
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 *   - MongoDB 7.x (docs/db-schema.md §3.3 컬렉션 정의 기준)
 * --------------------------------------------------
 * </pre>
 */
@Document(collection = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

  @Id private String feedbackId;

  @Indexed(unique = true, name = "uniq_feedbacks_message")
  private String messageId;

  private FeedbackRating rating;
  private String comment;
  private Instant createdAt;

  @Builder
  private Feedback(
      String feedbackId,
      String messageId,
      FeedbackRating rating,
      String comment,
      Instant createdAt) {
    this.feedbackId = feedbackId != null ? feedbackId : UUID.randomUUID().toString();
    this.messageId = messageId;
    this.rating = rating;
    this.comment = comment;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }
}
