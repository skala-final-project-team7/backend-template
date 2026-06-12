package com.lina.auth.token.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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
 * 작성목적 : user_groups 테이블 매핑(V002). 로그인(OAuth callback) 시 Confluence memberof API 로
 *           조회한 group 멤버십을 행 단위(groupId)로 영속한다. 애플리케이션이 user_key 로 모아
 *           JWT `groups` claim 배열로 집계한다(값 = groupId, name 아님).
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 1 — MySQL 영속 계층 (docs/db-schema.md §6.3)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6.5.x (복합 PK @IdClass)
 *   - MySQL 8.x (V002__create_user_groups.sql)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(name = "user_groups")
@IdClass(UserGroupId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGroup {

  /** users.user_key FK. 복합 PK 의 일부로 동일 group 중복 적재를 막는다. */
  @Id
  @Column(name = "user_key", nullable = false)
  private UUID userKey;

  /** Confluence groupId (memberof 응답 results[].id). group name 아님. */
  @Id
  @Column(name = "group_id", nullable = false, length = 128)
  private String groupId;

  @Builder
  private UserGroup(UUID userKey, String groupId) {
    this.userKey = userKey;
    this.groupId = groupId;
  }
}
