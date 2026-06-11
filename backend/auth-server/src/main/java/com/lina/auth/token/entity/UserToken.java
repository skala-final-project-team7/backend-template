package com.lina.auth.token.entity;

import com.lina.auth.token.TokenCipher;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
 * 작성목적 : user_tokens 테이블 매핑(V003). Confluence OAuth access/refresh token + cloudId 를
 *           사용자당 1:1 로 보관한다. 토큰 컬럼은 TokenCipher(AES-GCM) 로 암호화 저장하며(평문 금지),
 *           refresh 회전(rotating) 시 동일 레코드를 덮어쓴다(이전 값 미보존).
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 1 — MySQL 영속 계층 (docs/db-schema.md §6.2)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6.5.x (AttributeConverter 암호화 컬럼)
 *   - MySQL 8.x (V003__create_user_tokens.sql — VARBINARY(2048))
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(name = "user_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserToken {

  /** users.user_key FK 이자 PK (사용자당 Confluence 토큰셋 1개, 1:1). */
  @Id
  @Column(name = "user_key", nullable = false)
  private UUID userKey;

  /** Confluence OAuth access token. AES-GCM 암호화 저장. FE 미노출(서버 보관). */
  @Convert(converter = TokenCipher.class)
  @Column(name = "confluence_access_token_enc", nullable = false, length = 2048)
  private String confluenceAccessToken;

  /** Confluence OAuth refresh token. AES-GCM 암호화 저장. rotating — 갱신 시 덮어쓰기. */
  @Convert(converter = TokenCipher.class)
  @Column(name = "confluence_refresh_token_enc", nullable = false, length = 2048)
  private String confluenceRefreshToken;

  /** 게이트웨이 콘텐츠 조회 URL(api.atlassian.com/ex/confluence/{cloudId}/...) 구성용. 평문(민감 아님). */
  @Column(name = "cloud_id", nullable = false, length = 64)
  private String cloudId;

  /** access token 만료 시각(UTC). 임박 시 AUTH-03 refresh 로 갱신한다. */
  @Column(name = "access_token_expires_at", nullable = false)
  private Instant accessTokenExpiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Builder
  private UserToken(
      UUID userKey,
      String confluenceAccessToken,
      String confluenceRefreshToken,
      String cloudId,
      Instant accessTokenExpiresAt) {
    this.userKey = userKey;
    this.confluenceAccessToken = confluenceAccessToken;
    this.confluenceRefreshToken = confluenceRefreshToken;
    this.cloudId = cloudId;
    this.accessTokenExpiresAt = accessTokenExpiresAt;
  }

  /**
   * Rotating refresh — Atlassian 이 재발급한 access/refresh 토큰으로 동일 레코드를 덮어쓴다. 이전 refresh 는 Atlassian
   * 측에서 무효화되므로 보존하지 않는다(재사용 금지).
   */
  public void rotate(
      String confluenceAccessToken, String confluenceRefreshToken, Instant accessTokenExpiresAt) {
    this.confluenceAccessToken = confluenceAccessToken;
    this.confluenceRefreshToken = confluenceRefreshToken;
    this.accessTokenExpiresAt = accessTokenExpiresAt;
  }
}
