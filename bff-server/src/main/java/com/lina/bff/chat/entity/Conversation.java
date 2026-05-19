package com.lina.bff.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * 작성목적 : 대화방(Conversation) 엔티티. 사용자별 대화 단위를 표현하며 메시지의 부모이다.
 *           삭제는 hard delete 가 아닌 soft delete(deleted_at)로 처리해 연결된 피드백·QCA 데이터를 보존한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — conversations 테이블 매핑
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6 (java.time.Instant 매핑)
 *   - MySQL 8.x (docs/db-schema.md §3.1 DDL 기준)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(
    name = "conversations",
    indexes =
        @Index(
            name = "idx_conversations_user_active_recent",
            columnList = "user_id, deleted_at, last_message_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

  @Id
  @Column(name = "conversation_id", length = 36, nullable = false, updatable = false)
  private String conversationId;

  @Column(name = "user_id", length = 64, nullable = false)
  private String userId;

  @Column(name = "title", length = 255, nullable = false)
  private String title;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_message_at", nullable = false)
  private Instant lastMessageAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Builder
  private Conversation(
      String conversationId,
      String userId,
      String title,
      Instant createdAt,
      Instant updatedAt,
      Instant lastMessageAt,
      Instant deletedAt) {
    this.conversationId = conversationId;
    this.userId = userId;
    this.title = title;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.lastMessageAt = lastMessageAt;
    this.deletedAt = deletedAt;
  }

  /** 영속 직전 식별자/시각 기본값을 채운다. 명시적으로 설정된 값은 보존한다. */
  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (conversationId == null) {
      conversationId = UUID.randomUUID().toString();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = createdAt;
    }
    if (lastMessageAt == null) {
      lastMessageAt = createdAt;
    }
  }

  /** soft delete 처리. 실제 row 는 삭제하지 않고 deleted_at 만 채운다. */
  public void markDeleted() {
    this.deletedAt = Instant.now();
  }
}
