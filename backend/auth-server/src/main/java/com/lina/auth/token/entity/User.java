package com.lina.auth.token.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : users 테이블 매핑(V001). OAuth callback upsert 대상이며 role 은 JWT claim 의
 *           DB 단일 source. LINA 세션 access/refresh token 을 보관한다(Confluence 토큰 아님 — UserToken).
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 1 — MySQL 영속 계층 (docs/db-schema.md §6.1)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6.5.x (UUID ↔ MySQL BINARY(16) 기본 매핑)
 *   - MySQL 8.x (V001__create_users.sql)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  /** 내부 PK. 애플리케이션이 생성한 UUID 로 MySQL BINARY(16) 에 저장된다. */
  @Id
  @Column(name = "user_key", nullable = false)
  private UUID userKey;

  /** Confluence accountId. 문서/JWT/RAG 의 {@code userId} 와 동일 식별자(이메일 아님). */
  @Column(name = "user_id", nullable = false, unique = true, length = 128)
  private String userId;

  /** 로그인 이메일. admin-key Basic auth 의 adminEmail 로도 재사용된다(§6.4). */
  @Column(name = "email", nullable = false, unique = true)
  private String email;

  /** 표시 이름(Confluence 응답에서 저장). */
  @Column(name = "name", length = 128)
  private String name;

  @Column(name = "profile_image_url", length = 512)
  private String profileImageUrl;

  /** 권한 역할. JWT {@code role} claim 의 source of truth(DB 단일). */
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 16)
  private UserRole role;

  /** LINA 발급 access token(세션). Confluence 토큰은 UserToken 에 암호화 보관한다. */
  @Column(name = "access_token", nullable = false, length = 512)
  private String accessToken;

  /** LINA 발급 refresh token(세션 갱신). V001 선반영 컬럼 — 발급/회전은 Feature 4. */
  @Column(name = "refresh_token", length = 512)
  private String refreshToken;

  /** 최근 로그인 시각(UTC). OAuth callback 시 갱신된다. */
  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Builder
  private User(
      UUID userKey,
      String userId,
      String email,
      String name,
      String profileImageUrl,
      UserRole role,
      String accessToken,
      String refreshToken,
      Instant lastLoginAt) {
    this.userKey = userKey != null ? userKey : UUID.randomUUID();
    this.userId = userId;
    this.email = email;
    this.name = name;
    this.profileImageUrl = profileImageUrl;
    this.role = role != null ? role : UserRole.USER;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.lastLoginAt = lastLoginAt;
  }

  /**
   * OAuth callback 의 기존 사용자 upsert 갱신. userKey/userId/role 은 유지하고 프로필·LINA 세션 토큰·로그인 시각만 갱신한다(role
   * 변경은 DB 직접 UPDATE — docs/db-schema.md §6.1).
   */
  public void updateOnLogin(
      String name, String profileImageUrl, String accessToken, Instant lastLoginAt) {
    this.name = name;
    this.profileImageUrl = profileImageUrl;
    this.accessToken = accessToken;
    this.lastLoginAt = lastLoginAt;
  }
}
