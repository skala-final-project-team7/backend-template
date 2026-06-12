package com.lina.auth.oauth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Atlassian OAuth 2.0 (3LO) 운영 파라미터 홀더. client-id/secret·엔드포인트·scope 를 전부
 *           `${...}` 환경변수로 주입한다(application.yml `lina.oauth.confluence.*`, 평문 secret 금지).
 *           site-url 은 accessible-resources 멀티 사이트 시 명시 선택용(단일 사이트면 불필요).
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
@Getter
public class OAuthProperties {

  private final String clientId;

  /** OAuth client secret. 환경변수 주입 — 로그/응답 노출 금지. */
  private final String clientSecret;

  /** AUTH-01 인가 화면 URL (https://auth.atlassian.com/authorize). */
  private final String authorizationUri;

  /** AUTH-02/03 토큰 엔드포인트 (https://auth.atlassian.com/oauth/token). */
  private final String tokenUri;

  /** 사용자 프로필 조회 URL (https://api.atlassian.com/me) — accountId/email/name 취득. */
  private final String userInfoUri;

  private final String redirectUri;

  /** authorize scope (공백 구분 — offline_access 포함 필수, current-plans §구현 시 주의). */
  private final String scopes;

  /** AUTH-04/05 게이트웨이 base (https://api.atlassian.com). */
  private final String apiBaseUri;

  /** 멀티 사이트 시 선택할 사이트 base URL. 미설정이면 단일 사이트만 허용(임의 선택 금지). */
  private final String siteUrl;

  /** login→callback 상태(state) TTL(초). */
  private final long stateTtlSeconds;

  public OAuthProperties(
      @Value("${lina.oauth.confluence.client-id}") String clientId,
      @Value("${lina.oauth.confluence.client-secret}") String clientSecret,
      @Value("${lina.oauth.confluence.authorization-uri}") String authorizationUri,
      @Value("${lina.oauth.confluence.token-uri}") String tokenUri,
      @Value("${lina.oauth.confluence.user-info-uri}") String userInfoUri,
      @Value("${lina.oauth.confluence.redirect-uri}") String redirectUri,
      @Value("${lina.oauth.confluence.scopes}") String scopes,
      @Value("${lina.oauth.confluence.api-base-uri}") String apiBaseUri,
      @Value("${lina.oauth.confluence.site-url}") String siteUrl,
      @Value("${lina.oauth.state-ttl-seconds:600}") long stateTtlSeconds) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authorizationUri = authorizationUri;
    this.tokenUri = tokenUri;
    this.userInfoUri = userInfoUri;
    this.redirectUri = redirectUri;
    this.scopes = scopes;
    this.apiBaseUri = apiBaseUri;
    this.siteUrl = siteUrl;
    this.stateTtlSeconds = stateTtlSeconds;
  }
}
