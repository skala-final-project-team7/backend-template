package com.lina.auth.token.entity;

import com.lina.auth.token.TokenCipher;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * 작성목적 : admin_atlassian_credential 테이블 매핑(V004). Admin Key 수명주기 관리
 *           (activate/deactivate) 전용 정적 credential — site URL + admin API Token(AES-GCM 암호화).
 *           Basic auth = base64(users.email : 복호화한 API Token). 콘텐츠 조회용 OAuth 토큰(UserToken)
 *           과 자격증명·base URL 체계가 달라 분리 보관한다(혼동 금지).
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 1 — MySQL 영속 계층 (docs/db-schema.md §6.4)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Hibernate 6.5.x (AttributeConverter 암호화 컬럼)
 *   - MySQL 8.x (V004__create_admin_atlassian_credential.sql)
 * --------------------------------------------------
 * </pre>
 */
@Entity
@Table(name = "admin_atlassian_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAtlassianCredential {

  /** users.user_key FK 이자 PK (admin 사용자당 1:1). */
  @Id
  @Column(name = "user_key", nullable = false)
  private UUID userKey;

  /** Admin Key 관리 API base URL ({siteUrl}/wiki/api/v2/admin-key). 콘텐츠 조회 게이트웨이와 별개. */
  @Column(name = "site_url", nullable = false)
  private String siteUrl;

  /** Atlassian 계정 발급 API Token. AES-GCM 암호화 저장. 정적 credential — 만료/refresh 없음. */
  @Convert(converter = TokenCipher.class)
  @Column(name = "admin_api_token_enc", nullable = false, length = 2048)
  private String adminApiToken;

  @Builder
  private AdminAtlassianCredential(UUID userKey, String siteUrl, String adminApiToken) {
    this.userKey = userKey;
    this.siteUrl = siteUrl;
    this.adminApiToken = adminApiToken;
  }
}
