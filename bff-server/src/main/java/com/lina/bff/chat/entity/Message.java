package com.lina.bff.chat.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * 작성목적 : 메시지(Message) 문서. 사용자 질문과 AI 답변을 모두 한 컬렉션에 저장한다.
 *           assistant 메시지의 인용 출처는 별도 컬렉션 없이 sources 배열에 내장한다.
 *           조회는 soft delete(deletedAt == null) 필터를 항상 적용한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — messages 테이블 매핑(MySQL/JPA)
 *   - 2026-05-20, 데이터 저장소 변경, JPA @Entity → MongoDB @Document, sources 내장 배열 도입
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Data MongoDB 4.x
 *   - MongoDB 7.x (docs/db-schema.md §3.2 컬렉션 정의 기준)
 * --------------------------------------------------
 * </pre>
 */
@Document(collection = "messages")
@CompoundIndex(
    name = "idx_messages_conversation_active_created",
    def = "{'conversationId': 1, 'deletedAt': 1, 'createdAt': 1}")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

  @Id private String messageId;

  private String conversationId;
  private MessageRole role;
  private String content;
  private List<MessageSource> sources;
  private Double confidenceScore;
  private VerificationResult verificationResult;
  private Instant createdAt;
  private Instant deletedAt;

  @Builder
  private Message(
      String messageId,
      String conversationId,
      MessageRole role,
      String content,
      List<MessageSource> sources,
      Double confidenceScore,
      VerificationResult verificationResult,
      Instant createdAt,
      Instant deletedAt) {
    this.messageId = messageId != null ? messageId : UUID.randomUUID().toString();
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
    this.sources = sources != null ? new ArrayList<>(sources) : new ArrayList<>();
    this.confidenceScore = confidenceScore;
    this.verificationResult = verificationResult;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
    this.deletedAt = deletedAt;
  }

  /** 외부 변경 차단을 위해 sources 는 방어 사본을 반환한다. */
  public List<MessageSource> getSources() {
    return Collections.unmodifiableList(sources);
  }

  /** soft delete 처리. 실제 문서는 삭제하지 않고 deletedAt 만 채운다. */
  public void markDeleted() {
    this.deletedAt = Instant.now();
  }
}
