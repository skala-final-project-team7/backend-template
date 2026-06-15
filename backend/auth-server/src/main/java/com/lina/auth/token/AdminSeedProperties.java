package com.lina.auth.token;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 최초 admin 사전 seed 값 홀더(로그인 전 주입). accountId/email/name/site-url 은 admin 의
 *           Atlassian 계정 정보, api-token 은 admin-key 관리용 API Token(secret). 전부 `${...}` env 주입이며
 *           api-token 은 평문으로 받아 AdminSeeder 가 엔티티에 담아 저장(TokenCipher 가 암호화). 하나라도
 *           비어 있으면 seed 를 건너뛴다(로컬 등 admin 불필요 환경). 평문 secret 을 코드/SQL 에 하드코딩하지 않기 위한 경로.
 * 작성일 : 2026-06-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-15, 최초 작성, 3단계 admin seed (docs/db-schema.md §6.1)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
@Getter
public class AdminSeedProperties {

  /** admin 의 Confluence accountId(=users.user_id). */
  private final String accountId;

  /** admin 이메일(=users.email, admin-key Basic auth ID). */
  private final String email;

  /** 표시 이름(=users.name). */
  private final String name;

  /** Confluence site URL(=admin_atlassian_credential.site_url). */
  private final String siteUrl;

  /** admin API Token(secret) — 평문 주입, 저장 시 TokenCipher 암호화. 로그 금지. */
  private final String apiToken;

  public AdminSeedProperties(
      @Value("${lina.admin-seed.account-id:}") String accountId,
      @Value("${lina.admin-seed.email:}") String email,
      @Value("${lina.admin-seed.name:}") String name,
      @Value("${lina.admin-seed.site-url:}") String siteUrl,
      @Value("${lina.admin-seed.api-token:}") String apiToken) {
    this.accountId = accountId;
    this.email = email;
    this.name = name;
    this.siteUrl = siteUrl;
    this.apiToken = apiToken;
  }

  /** 필수값(name 제외)이 모두 채워졌는지 — 하나라도 비면 seed 를 건너뛴다. */
  public boolean isConfigured() {
    return hasText(accountId) && hasText(email) && hasText(siteUrl) && hasText(apiToken);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
