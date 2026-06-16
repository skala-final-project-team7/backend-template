package com.lina.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lina.auth.internal.dto.AdminConfluenceCredentialResponse;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.oauth.dto.RefreshTokenRequest;
import com.lina.auth.oauth.dto.TokenExchangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Feature 7 — 토큰/secret 을 보유한 DTO 가 toString()(=실수로 log.info("{}", dto) 했을 때 찍히는 값)으로 원문을 노출하지 않음을
 * 고정한다. Confluence access/refresh, LINA refresh, client-secret 모두 마스킹 대상. non-secret 디버그
 * 필드(cloudId, siteUrl, grantType 등)는 유지한다.
 */
class SensitiveLoggingTest {

  private static final String SECRET = "tok_SUPERSECRET_value_1234567890";

  @Test
  @DisplayName("AtlassianTokenResponse.toString() 은 access/refresh 토큰을 마스킹한다")
  void atlassianTokenResponseMasksTokens() {
    String rendered = new AtlassianTokenResponse(SECRET, SECRET, 3600, "read:me").toString();
    assertThat(rendered).doesNotContain(SECRET).contains("***");
    assertThat(rendered).contains("read:me"); // non-secret 필드는 유지
  }

  @Test
  @DisplayName("LoginTokenResponse.toString() 은 LINA access/refresh 토큰을 마스킹한다")
  void loginTokenResponseMasksTokens() {
    String rendered =
        new LoginTokenResponse(SECRET, SECRET, "2026-06-16T10:00:00+09:00").toString();
    assertThat(rendered).doesNotContain(SECRET).contains("***");
    assertThat(rendered).contains("2026-06-16T10:00:00+09:00");
  }

  @Test
  @DisplayName("RefreshTokenRequest.toString() 은 refresh 토큰을 마스킹한다")
  void refreshTokenRequestMasksToken() {
    String rendered = new RefreshTokenRequest(SECRET).toString();
    assertThat(rendered).doesNotContain(SECRET).contains("***");
  }

  @Test
  @DisplayName(
      "AdminConfluenceCredentialResponse.toString() 은 accessToken 만 마스킹하고 cloudId/siteUrl 은 유지한다")
  void credentialResponseMasksAccessTokenOnly() {
    String rendered =
        new AdminConfluenceCredentialResponse(
                SECRET, "cloud-123", "https://acme.atlassian.net", "2026-06-16T10:00:00+09:00")
            .toString();
    assertThat(rendered).doesNotContain(SECRET).contains("***");
    assertThat(rendered).contains("cloud-123").contains("https://acme.atlassian.net");
  }

  @Test
  @DisplayName(
      "TokenExchangeRequest.toString() 은 client-secret/code/refresh 를 마스킹하고 grantType 은 노출한다")
  void tokenExchangeRequestMasksSecrets() {
    String rendered =
        TokenExchangeRequest.builder()
            .grantType("authorization_code")
            .clientId("client-abc")
            .clientSecret(SECRET)
            .code(SECRET)
            .refreshToken(SECRET)
            .redirectUri("https://localhost/cb")
            .build()
            .toString();
    assertThat(rendered).doesNotContain(SECRET).contains("***");
    assertThat(rendered).contains("authorization_code").contains("client-abc");
  }

  @Test
  @DisplayName("토큰 DTO 를 로거로 출력해도 토큰 원문이 로그에 남지 않는다")
  void loggingTokenDtoDoesNotEmitToken() {
    Logger logger = (Logger) LoggerFactory.getLogger("test.sensitive.logging");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    logger.info("token exchange result: {}", new AtlassianTokenResponse(SECRET, SECRET, 3600, "s"));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain(SECRET).contains("***");
  }
}
