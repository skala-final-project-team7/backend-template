package com.lina.auth.token.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.auth.token.TokenCipher;
import com.lina.auth.token.entity.UserToken;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(TokenCipher.class)
class UserTokenRepositoryTest {

  @Autowired private UserTokenRepository userTokenRepository;
  @Autowired private EntityManager entityManager;

  private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final Instant EXPIRES_AT = Instant.parse("2026-06-11T01:00:00Z");

  private UserToken token(String accessToken, String refreshToken, Instant expiresAt) {
    return UserToken.builder()
        .userKey(USER_KEY)
        .confluenceAccessToken(accessToken)
        .confluenceRefreshToken(refreshToken)
        .cloudId("cloud-1234")
        .accessTokenExpiresAt(expiresAt)
        .build();
  }

  @Test
  @DisplayName("Confluence 토큰은 암호화 저장 후 조회 시 평문으로 복원된다(라운드트립)")
  void shouldRoundTripEncryptedTokens() {
    userTokenRepository.saveAndFlush(token("conf-access-1", "conf-refresh-1", EXPIRES_AT));
    entityManager.clear();

    UserToken found = userTokenRepository.findById(USER_KEY).orElseThrow();

    assertThat(found.getConfluenceAccessToken()).isEqualTo("conf-access-1");
    assertThat(found.getConfluenceRefreshToken()).isEqualTo("conf-refresh-1");
    assertThat(found.getCloudId()).isEqualTo("cloud-1234");
  }

  @Test
  @DisplayName("DB 에 저장된 토큰 컬럼 값은 평문이 아니다")
  void shouldNotStoreTokensAsPlaintext() {
    userTokenRepository.saveAndFlush(token("conf-access-1", "conf-refresh-1", EXPIRES_AT));
    entityManager.clear();

    byte[] storedAccess =
        (byte[])
            entityManager
                .createNativeQuery(
                    "select confluence_access_token_enc from user_tokens where user_key = ?")
                .setParameter(1, USER_KEY)
                .getSingleResult();

    assertThat(storedAccess).isNotEqualTo("conf-access-1".getBytes(StandardCharsets.UTF_8));
    assertThat(new String(storedAccess, StandardCharsets.ISO_8859_1))
        .doesNotContain("conf-access-1");
  }

  @Test
  @DisplayName("refresh 회전 시 기존 레코드를 덮어쓰고 이전 토큰 값은 보존하지 않는다")
  void shouldOverwriteTokensOnRotation() {
    userTokenRepository.saveAndFlush(token("conf-access-1", "conf-refresh-1", EXPIRES_AT));
    entityManager.clear();

    Instant rotatedExpiresAt = Instant.parse("2026-06-11T02:00:00Z");
    UserToken stored = userTokenRepository.findById(USER_KEY).orElseThrow();
    stored.rotate("conf-access-2", "conf-refresh-2", rotatedExpiresAt);
    userTokenRepository.saveAndFlush(stored);
    entityManager.clear();

    UserToken found = userTokenRepository.findById(USER_KEY).orElseThrow();
    assertThat(found.getConfluenceAccessToken()).isEqualTo("conf-access-2");
    assertThat(found.getConfluenceRefreshToken()).isEqualTo("conf-refresh-2");
    assertThat(found.getAccessTokenExpiresAt()).isEqualTo(rotatedExpiresAt);
    assertThat(userTokenRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("실제 Atlassian 크기(2KB 초과)의 토큰도 잘림 없이 저장된다 — 라이브 스모크 회귀(2026-06-12)")
  void shouldPersistRealWorldSizedTokens() {
    // 실측: Atlassian access token 은 2KB 를 넘는 JWT — 암호화(+28B) 후 VARBINARY(2048) 초과로 INSERT 실패했다
    String longAccessToken = "conf-access-".repeat(400); // 4,800자
    String longRefreshToken = "conf-refresh-".repeat(300); // 3,900자

    userTokenRepository.saveAndFlush(token(longAccessToken, longRefreshToken, EXPIRES_AT));
    entityManager.clear();

    UserToken found = userTokenRepository.findById(USER_KEY).orElseThrow();
    assertThat(found.getConfluenceAccessToken()).isEqualTo(longAccessToken);
    assertThat(found.getConfluenceRefreshToken()).isEqualTo(longRefreshToken);
  }

  @Test
  @DisplayName("access token 만료 시각이 저장되고 user_key 로 조회된다")
  void shouldPersistAccessTokenExpiry() {
    userTokenRepository.saveAndFlush(token("conf-access-1", "conf-refresh-1", EXPIRES_AT));
    entityManager.clear();

    UserToken found = userTokenRepository.findById(USER_KEY).orElseThrow();

    assertThat(found.getAccessTokenExpiresAt()).isEqualTo(EXPIRES_AT);
  }
}
