package com.lina.bff.chat.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 대화방(Conversation) 문서. 사용자별 대화 단위. 메시지는 별도 컬렉션(messages)으로 분리.
 *           삭제는 hard delete 가 아닌 soft delete(deletedAt)로 처리해 연결된 피드백·QCA 데이터를 보존한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — conversations 테이블 매핑(MySQL/JPA)
 *   - 2026-05-20, 데이터 저장소 변경, JPA @Entity → MongoDB @Document 로 재구현
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 *   - MongoDB 7.x (docs/db-schema.md §3.1 컬렉션 정의 기준)
 * --------------------------------------------------
 * </pre>
 */
@Document(collection = "conversations")
@CompoundIndex(
    name = "idx_conversations_user_active_recent",
    def = "{'userId': 1, 'deletedAt': 1, 'lastMessageAt': -1}")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

  @Id private String conversationId;

  private String userId;
  private String title;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastMessageAt;
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
    Instant now = Instant.now();
    this.conversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();
    this.userId = userId;
    this.title = title;
    this.createdAt = createdAt != null ? createdAt : now;
    this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    this.lastMessageAt = lastMessageAt != null ? lastMessageAt : this.createdAt;
    this.deletedAt = deletedAt;
  }

  /** soft delete 처리. 실제 문서는 삭제하지 않고 deletedAt 만 채운다. */
  public void markDeleted() {
    this.deletedAt = Instant.now();
  }
}
