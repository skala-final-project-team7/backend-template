package com.lina.bff.chat.entity;

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
 * 작성목적 : assistant 메시지에 내장되는 인용 출처 값 객체.
 *           별도 컬렉션 없이 Message 문서의 sources 배열로 임베드되어 저장/조회된다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — message_sources 테이블 매핑(MySQL/JPA)
 *   - 2026-05-20, 데이터 저장소 변경, 별도 엔티티 → Message 문서 내장 값 객체로 단순화
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Data MongoDB 4.x (POJO 매핑)
 * --------------------------------------------------
 * </pre>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSource {

  private String title;
  private String pageId;
  private String spaceId;
  private String spaceName;
  private String url;
  private Instant sourceUpdatedAt;
  private Double relevanceScore;

  @Builder
  private MessageSource(
      String title,
      String pageId,
      String spaceId,
      String spaceName,
      String url,
      Instant sourceUpdatedAt,
      Double relevanceScore) {
    this.title = title;
    this.pageId = pageId;
    this.spaceId = spaceId;
    this.spaceName = spaceName;
    this.url = url;
    this.sourceUpdatedAt = sourceUpdatedAt;
    this.relevanceScore = relevanceScore;
  }
}
