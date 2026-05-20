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

  /** 피드백 식별자. 애플리케이션이 생성한 UUID 문자열이며 MongoDB `_id`로 매핑된다. */
  @Id private String feedbackId;

  /** 피드백 대상 assistant 메시지 식별자. UNIQUE 인덱스로 메시지당 1건만 허용된다. */
  @Indexed(unique = true, name = "uniq_feedbacks_message")
  private String messageId;

  /** 사용자 평가(`LIKE` / `DISLIKE`). API 의 "like"/"dislike" 와 매핑된다. */
  private FeedbackRating rating;

  /** 사용자 코멘트(선택). 부정 피드백 원인 분석에 활용되며 입력하지 않을 수 있다. */
  private String comment;

  /** 피드백 생성 시각(UTC). upsert(갱신)되더라도 최초 생성 시각을 유지한다. */
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
