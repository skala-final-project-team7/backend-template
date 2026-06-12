package com.lina.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lina.auth.internal.dto.AdminConfluenceCredentialResponse;
import com.lina.auth.oauth.AtlassianOAuthClient;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.entity.UserToken;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.auth.token.repository.UserTokenRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Feature 5 — admin credential 조회 비즈니스 로직 검증. role 분기·만료 시 AUTH-03 refresh 후 rotating 덮어쓰기·에러
 * 매핑(401/403/404/502)을 단위 수준에서 고정한다(Atlassian 실 호출 없음 — client 는 mock).
 */
@ExtendWith(MockitoExtension.class)
class InternalCredentialServiceTest {

  private static final String ADMIN_USER_ID = "712020:admin";
  private static final UUID USER_KEY = UUID.randomUUID();
  private static final String CLOUD_ID = "11111111-2222-3333-4444-555555555555";
  private static final String SITE_URL = "https://your-site.atlassian.net";

  @Mock private AtlassianOAuthClient oauthClient;
  @Mock private UserRepository userRepository;
  @Mock private UserTokenRepository userTokenRepository;
  @Mock private AdminAtlassianCredentialRepository adminCredentialRepository;

  private final PlatformTransactionManager transactionManager =
      new AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
          return false;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
      };

  private InternalCredentialService service() {
    return new InternalCredentialService(
        oauthClient,
        userRepository,
        userTokenRepository,
        adminCredentialRepository,
        transactionManager);
  }

  private User user(UserRole role) {
    return User.builder()
        .userKey(USER_KEY)
        .userId(ADMIN_USER_ID)
        .email("admin@example.com")
        .role(role)
        .accessToken("lina-access")
        .build();
  }

  private UserToken userToken(Instant accessTokenExpiresAt) {
    return UserToken.builder()
        .userKey(USER_KEY)
        .confluenceAccessToken("conf-access")
        .confluenceRefreshToken("conf-refresh")
        .cloudId(CLOUD_ID)
        .accessTokenExpiresAt(accessTokenExpiresAt)
        .build();
  }

  private AdminAtlassianCredential adminCredential() {
    return AdminAtlassianCredential.builder()
        .userKey(USER_KEY)
        .siteUrl(SITE_URL)
        .adminApiToken("admin-api-token")
        .build();
  }

  private void givenAdminWithToken(Instant accessTokenExpiresAt) {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.ADMIN)));
    given(userTokenRepository.findById(USER_KEY))
        .willReturn(Optional.of(userToken(accessTokenExpiresAt)));
    given(adminCredentialRepository.findById(USER_KEY)).willReturn(Optional.of(adminCredential()));
  }

  // --- 성공: 유효 토큰 ---

  @Test
  @DisplayName("토큰이 유효하면 refresh 없이 저장된 accessToken/cloudId/siteUrl 을 반환한다")
  void shouldReturnStoredCredentialWithoutRefresh() {
    givenAdminWithToken(Instant.now().plus(1, ChronoUnit.HOURS));

    AdminConfluenceCredentialResponse response = service().getAdminCredential(ADMIN_USER_ID);

    assertThat(response.accessToken()).isEqualTo("conf-access");
    assertThat(response.cloudId()).isEqualTo(CLOUD_ID);
    assertThat(response.siteUrl()).isEqualTo(SITE_URL);
    assertThat(response.expiresAt()).endsWith("+09:00"); // KST 표기 (api-spec Common)
    verify(oauthClient, never()).refreshAccessToken(anyString());
    verify(userTokenRepository, never()).save(any());
  }

  // --- 성공: 만료/임박 → AUTH-03 refresh ---

  @Test
  @DisplayName("토큰이 만료됐으면 AUTH-03 refresh 후 rotating 덮어쓰기 저장하고 새 토큰을 반환한다")
  void shouldRefreshAndPersistWhenExpired() {
    givenAdminWithToken(Instant.now().minus(1, ChronoUnit.MINUTES));
    given(oauthClient.refreshAccessToken("conf-refresh"))
        .willReturn(
            new AtlassianTokenResponse("new-access", "new-refresh", 3600, "read:confluence-user"));

    AdminConfluenceCredentialResponse response = service().getAdminCredential(ADMIN_USER_ID);

    assertThat(response.accessToken()).isEqualTo("new-access");
    verify(userTokenRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                saved ->
                    saved.getConfluenceAccessToken().equals("new-access")
                        && saved.getConfluenceRefreshToken().equals("new-refresh")));
  }

  @Test
  @DisplayName("만료 임박(skew 이내) 토큰도 refresh 한다")
  void shouldRefreshWhenExpiryImminent() {
    givenAdminWithToken(Instant.now().plus(10, ChronoUnit.SECONDS));
    given(oauthClient.refreshAccessToken("conf-refresh"))
        .willReturn(
            new AtlassianTokenResponse("new-access", "new-refresh", 3600, "read:confluence-user"));

    AdminConfluenceCredentialResponse response = service().getAdminCredential(ADMIN_USER_ID);

    assertThat(response.accessToken()).isEqualTo("new-access");
  }

  // --- 에러 정책 ---

  @Test
  @DisplayName("사용자가 없으면 404 RESOURCE_NOT_FOUND")
  void shouldThrow404WhenUserNotFound() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getAdminCredential(ADMIN_USER_ID))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName("role 이 ADMIN 이 아니면 403 FORBIDDEN — credential 로드·Atlassian 호출 없음")
  void shouldThrow403WhenNotAdmin() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.USER)));

    assertThatThrownBy(() -> service().getAdminCredential(ADMIN_USER_ID))
        .isInstanceOfSatisfying(
            BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(userTokenRepository, never()).findById(any());
    verify(oauthClient, never()).refreshAccessToken(anyString());
  }

  @Test
  @DisplayName("user_tokens 가 없으면 404 RESOURCE_NOT_FOUND")
  void shouldThrow404WhenUserTokenMissing() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.ADMIN)));
    given(userTokenRepository.findById(USER_KEY)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getAdminCredential(ADMIN_USER_ID))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName("admin_atlassian_credential(siteUrl)이 없으면 404 RESOURCE_NOT_FOUND")
  void shouldThrow404WhenAdminCredentialMissing() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.ADMIN)));
    given(userTokenRepository.findById(USER_KEY))
        .willReturn(Optional.of(userToken(Instant.now().plus(1, ChronoUnit.HOURS))));
    given(adminCredentialRepository.findById(USER_KEY)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getAdminCredential(ADMIN_USER_ID))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName("refresh 가 invalid_grant 면 401 UNAUTHORIZED (재로그인 필요)")
  void shouldThrow401OnInvalidGrant() {
    givenAdminWithToken(Instant.now().minus(1, ChronoUnit.MINUTES));
    given(oauthClient.refreshAccessToken("conf-refresh"))
        .willThrow(new AtlassianOAuthClient.InvalidGrantException("invalid_grant"));

    assertThatThrownBy(() -> service().getAdminCredential(ADMIN_USER_ID))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    verify(userTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("refresh 일시 장애(5xx 등)는 502 EXTERNAL_SERVICE_ERROR")
  void shouldThrow502OnTransientAtlassianFailure() {
    givenAdminWithToken(Instant.now().minus(1, ChronoUnit.MINUTES));
    given(oauthClient.refreshAccessToken("conf-refresh"))
        .willThrow(new AtlassianOAuthClient.AtlassianOAuthException("Atlassian 5xx"));

    assertThatThrownBy(() -> service().getAdminCredential(ADMIN_USER_ID))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
    verify(userTokenRepository, never()).save(any());
  }
}
