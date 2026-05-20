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

  /** 인용된 Confluence 페이지 제목. Frontend 출처 카드에 그대로 표시된다. */
  private String title;

  /** Confluence 페이지 ID. 원본 링크 생성 및 미리보기 API 호출 시 키로 사용된다. */
  private String pageId;

  /** 페이지가 속한 Confluence 스페이스 ID. ACL/필터링 메타데이터. */
  private String spaceId;

  /** 페이지가 속한 스페이스의 표시명. Frontend 출처 카드에 표시된다. */
  private String spaceName;

  /** Confluence 원본 페이지 URL. Frontend 의 "원본 열기" 액션 대상. */
  private String url;

  /** Confluence 페이지 최종 수정 시각(UTC). 출처 신선도 표시에 사용된다. */
  private Instant sourceUpdatedAt;

  /** RAG 검색이 산출한 관련도 점수(0.0 ~ 1.0). 출처 정렬·필터링에 사용된다. */
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
