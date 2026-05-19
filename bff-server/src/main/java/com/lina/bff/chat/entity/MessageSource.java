package com.lina.bff.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
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
 * 작성목적 : assistant 메시지의 인용 출처(Confluence 페이지 제목/스페이스/링크/수정일/관련도) 엔티티.
 *           메시지 1건에 N개의 출처가 종속되며, surrogate key(BIGINT)를 PK 로 사용한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — message_sources 테이블 매핑
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6
 *   - MySQL 8.x (docs/db-schema.md §3.3 DDL 기준)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(
    name = "message_sources",
    indexes = @Index(name = "idx_message_sources_message", columnList = "message_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSource {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "source_id", nullable = false, updatable = false)
  private Long sourceId;

  @Column(name = "message_id", length = 36, nullable = false)
  private String messageId;

  @Column(name = "title", length = 512, nullable = false)
  private String title;

  @Column(name = "page_id", length = 64, nullable = false)
  private String pageId;

  @Column(name = "space_id", length = 64)
  private String spaceId;

  @Column(name = "space_name", length = 255)
  private String spaceName;

  @Column(name = "url", length = 1024)
  private String url;

  @Column(name = "source_updated_at")
  private Instant sourceUpdatedAt;

  @Column(name = "relevance_score")
  private Double relevanceScore;

  @Builder
  private MessageSource(
      String messageId,
      String title,
      String pageId,
      String spaceId,
      String spaceName,
      String url,
      Instant sourceUpdatedAt,
      Double relevanceScore) {
    this.messageId = messageId;
    this.title = title;
    this.pageId = pageId;
    this.spaceId = spaceId;
    this.spaceName = spaceName;
    this.url = url;
    this.sourceUpdatedAt = sourceUpdatedAt;
    this.relevanceScore = relevanceScore;
  }
}
