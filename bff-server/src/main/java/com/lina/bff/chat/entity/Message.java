package com.lina.bff.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
 * 작성목적 : 메시지(Message) 엔티티. 사용자 질문과 AI 답변을 모두 한 테이블에 저장한다.
 *           assistant 메시지는 신뢰도 점수/검증 결과를 가질 수 있으며, 인용 출처는 MessageSource 로 분리한다.
 *           조회는 soft delete(deleted_at IS NULL) 필터를 항상 적용한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — messages 테이블 매핑
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6 (java.time.Instant, @Lob → LONGTEXT)
 *   - MySQL 8.x (docs/db-schema.md §3.2 DDL 기준)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(
    name = "messages",
    indexes =
        @Index(
            name = "idx_messages_conversation_created",
            columnList = "conversation_id, deleted_at, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

  @Id
  @Column(name = "message_id", length = 36, nullable = false, updatable = false)
  private String messageId;

  @Column(name = "conversation_id", length = 36, nullable = false)
  private String conversationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 16, nullable = false)
  private MessageRole role;

  @Lob
  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "confidence_score")
  private Double confidenceScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_result", length = 32)
  private VerificationResult verificationResult;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Builder
  private Message(
      String messageId,
      String conversationId,
      MessageRole role,
      String content,
      Double confidenceScore,
      VerificationResult verificationResult,
      Instant createdAt,
      Instant deletedAt) {
    this.messageId = messageId;
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
    this.confidenceScore = confidenceScore;
    this.verificationResult = verificationResult;
    this.createdAt = createdAt;
    this.deletedAt = deletedAt;
  }

  /** 영속 직전 식별자/생성 시각 기본값을 채운다. 명시적으로 설정된 값은 보존한다. */
  @PrePersist
  void onCreate() {
    if (messageId == null) {
      messageId = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  /** soft delete 처리. 실제 row 는 삭제하지 않고 deleted_at 만 채운다. */
  public void markDeleted() {
    this.deletedAt = Instant.now();
  }
}
