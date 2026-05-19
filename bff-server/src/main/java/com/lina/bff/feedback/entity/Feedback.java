package com.lina.bff.feedback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 답변(assistant 메시지) 단위 피드백 엔티티. 메시지당 1건만 허용(uniq_feedbacks_message).
 *           재등록은 동일 row upsert 로 처리한다(신규/갱신 분기는 Feature 6 Service 책임).
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — feedbacks 테이블 매핑
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6
 *   - MySQL 8.x (docs/db-schema.md §3.4 DDL 기준)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(
    name = "feedbacks",
    uniqueConstraints =
        @UniqueConstraint(name = "uniq_feedbacks_message", columnNames = "message_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

  @Id
  @Column(name = "feedback_id", length = 36, nullable = false, updatable = false)
  private String feedbackId;

  @Column(name = "message_id", length = 36, nullable = false)
  private String messageId;

  @Enumerated(EnumType.STRING)
  @Column(name = "rating", length = 16, nullable = false)
  private FeedbackRating rating;

  @Column(name = "comment", length = 1000)
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Builder
  private Feedback(
      String feedbackId,
      String messageId,
      FeedbackRating rating,
      String comment,
      Instant createdAt) {
    this.feedbackId = feedbackId;
    this.messageId = messageId;
    this.rating = rating;
    this.comment = comment;
    this.createdAt = createdAt;
  }

  /** 영속 직전 식별자/생성 시각 기본값을 채운다. 명시적으로 설정된 값은 보존한다. */
  @PrePersist
  void onCreate() {
    if (feedbackId == null) {
      feedbackId = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
