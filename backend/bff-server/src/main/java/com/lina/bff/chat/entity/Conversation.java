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
    def = "{'userId': 1, 'deletedAt': 1, 'isPinned': -1, 'lastMessageAt': -1}")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

  /** 대화방 식별자. 애플리케이션이 생성한 UUID 문자열이며 MongoDB `_id`로 매핑된다. */
  @Id private String conversationId;

  /** 대화 소유자(사용자) 식별자. 2단계에서는 고정 데모 사용자, 3단계 이후 JWT Claim `userId` 값. */
  private String userId;

  /** 대화 제목. 생성 시 기본 제목으로 시작하며 사용자가 수정할 수 있다. */
  private String title;

  /** 대화 생성 시각(UTC). 생성 후 변경하지 않는다. */
  private Instant createdAt;

  /** 대화 메타데이터(제목 등) 최종 수정 시각(UTC). */
  private Instant updatedAt;

  /** 가장 최근 메시지가 추가된 시각(UTC). 대화 목록 정렬 키로 사용된다. */
  private Instant lastMessageAt;

  /** 채팅방 고정 여부. 새 대화는 기본 false 로 생성된다. */
  private boolean isPinned;

  /** soft delete 시각(UTC). null 이면 활성 대화. 모든 조회는 `deletedAt == null` 필터 적용. */
  private Instant deletedAt;

  @Builder
  private Conversation(
      String conversationId,
      String userId,
      String title,
      Instant createdAt,
      Instant updatedAt,
      Instant lastMessageAt,
      Boolean isPinned,
      Instant deletedAt) {
    Instant now = Instant.now();
    this.conversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();
    this.userId = userId;
    this.title = title;
    this.createdAt = createdAt != null ? createdAt : now;
    this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    this.lastMessageAt = lastMessageAt != null ? lastMessageAt : this.createdAt;
    this.isPinned = isPinned != null ? isPinned : false;
    this.deletedAt = deletedAt;
  }

  /** soft delete 처리. 실제 문서는 삭제하지 않고 deletedAt 만 채운다. */
  public void markDeleted() {
    this.deletedAt = Instant.now();
  }
}
