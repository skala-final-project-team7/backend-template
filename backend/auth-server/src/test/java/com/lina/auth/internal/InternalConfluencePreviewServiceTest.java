package com.lina.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lina.auth.internal.dto.ConfluencePagePreviewResponse;
import com.lina.auth.oauth.AtlassianOAuthClient;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.ConfluencePageV2Response;
import com.lina.auth.oauth.dto.ConfluenceSpaceV2Response;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.entity.UserToken;
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
 * Feature P2 — Confluence 미리보기 내부 프록시 비즈니스 로직 검증. 토큰 조회·만료 시 AUTH-03 refresh·미리보기 매핑·에러
 * 매핑(401/404/502)을 단위 수준에서 고정한다(Atlassian 실 호출 없음 — client 는 mock).
 */
@ExtendWith(MockitoExtension.class)
class InternalConfluencePreviewServiceTest {

  private static final String USER_ID = "712020:abc";
  private static final UUID USER_KEY = UUID.randomUUID();
  private static final String CLOUD_ID = "11111111-2222-3333-4444-555555555555";
  private static final String PAGE_ID = "12345";
  private static final String SPACE_ID = "2850818";

  @Mock private AtlassianOAuthClient oauthClient;
  @Mock private UserRepository userRepository;
  @Mock private UserTokenRepository userTokenRepository;

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

  private InternalConfluencePreviewService service() {
    return new InternalConfluencePreviewService(
        oauthClient, userRepository, userTokenRepository, transactionManager);
  }

  private User user() {
    return User.builder()
        .userKey(USER_KEY)
        .userId(USER_ID)
        .email("user@example.com")
        .role(UserRole.USER)
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

  private ConfluencePageV2Response pageV2() {
    return new ConfluencePageV2Response(
        PAGE_ID,
        "S3 트러블슈팅 가이드",
        SPACE_ID,
        new ConfluencePageV2Response.Version("2026-04-15T09:30:00.000Z"),
        new ConfluencePageV2Response.Body(
            new ConfluencePageV2Response.Body.View("<h1>S3</h1><p>권한 오류는...</p>")),
        new ConfluencePageV2Response.Links(
            "https://team.atlassian.net/wiki", "/spaces/CCC/pages/12345/S3"));
  }

  private ConfluenceSpaceV2Response spaceV2() {
    return new ConfluenceSpaceV2Response(SPACE_ID, "CLOUD", "Cloud Platform");
  }

  private void givenUserWithToken(Instant accessTokenExpiresAt) {
    given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user()));
    given(userTokenRepository.findById(USER_KEY))
        .willReturn(Optional.of(userToken(accessTokenExpiresAt)));
  }

  // --- 성공: 유효 토큰 → refresh 없이 매핑 ---

  @Test
  @DisplayName(
      "유효 토큰이면 refresh 없이 v2 page+space 를 매핑한다(breadcrumbs=[space,title]·updatedAt KST·author null)")
  void shouldMapPreviewWithoutRefresh() {
    givenUserWithToken(Instant.now().plus(1, ChronoUnit.HOURS));
    given(oauthClient.fetchPageV2("conf-access", CLOUD_ID, PAGE_ID)).willReturn(pageV2());
    given(oauthClient.fetchSpaceV2("conf-access", CLOUD_ID, SPACE_ID)).willReturn(spaceV2());

    ConfluencePagePreviewResponse response = service().getPagePreview(USER_ID, PAGE_ID);

    assertThat(response.pageId()).isEqualTo(PAGE_ID);
    assertThat(response.title()).isEqualTo("S3 트러블슈팅 가이드");
    assertThat(response.spaceName()).isEqualTo("Cloud Platform");
    assertThat(response.authorName()).isNull(); // v2 는 authorId 만 — displayName 미해석(현재 null)
    assertThat(response.updatedAt()).isEqualTo("2026-04-15T18:30:00+09:00"); // 09:30Z → KST
    assertThat(response.breadcrumbs()).containsExactly("Cloud Platform", "S3 트러블슈팅 가이드");
    assertThat(response.pageUrl())
        .isEqualTo("https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3");
    assertThat(response.bodyViewValue()).isEqualTo("<h1>S3</h1><p>권한 오류는...</p>");
    verify(oauthClient, never()).refreshAccessToken(anyString());
  }

  // --- 성공: 만료 → AUTH-03 refresh 후 새 토큰으로 조회 ---

  @Test
  @DisplayName("토큰이 만료됐으면 AUTH-03 refresh 후 새 accessToken 으로 content 를 조회한다")
  void shouldRefreshThenFetchWhenExpired() {
    givenUserWithToken(Instant.now().minus(1, ChronoUnit.MINUTES));
    given(oauthClient.refreshAccessToken("conf-refresh"))
        .willReturn(
            new AtlassianTokenResponse("new-access", "new-refresh", 3600, "read:confluence-user"));
    given(oauthClient.fetchPageV2("new-access", CLOUD_ID, PAGE_ID)).willReturn(pageV2());
    given(oauthClient.fetchSpaceV2("new-access", CLOUD_ID, SPACE_ID)).willReturn(spaceV2());

    ConfluencePagePreviewResponse response = service().getPagePreview(USER_ID, PAGE_ID);

    assertThat(response.title()).isEqualTo("S3 트러블슈팅 가이드");
    verify(oauthClient).refreshAccessToken("conf-refresh");
    verify(userTokenRepository).save(any(UserToken.class));
    verify(oauthClient).fetchPageV2("new-access", CLOUD_ID, PAGE_ID);
  }

  // --- 실패 매핑 ---

  @Test
  @DisplayName("사용자가 없으면 404 RESOURCE_NOT_FOUND")
  void shouldReturn404WhenUserMissing() {
    given(userRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("저장된 Confluence 토큰이 없으면 404 RESOURCE_NOT_FOUND")
  void shouldReturn404WhenTokenMissing() {
    given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user()));
    given(userTokenRepository.findById(USER_KEY)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("페이지 없음/접근 불가(ContentNotAccessible)는 404 RESOURCE_NOT_FOUND 로 통일한다(비노출)")
  void shouldReturn404WhenContentNotAccessible() {
    givenUserWithToken(Instant.now().plus(1, ChronoUnit.HOURS));
    given(oauthClient.fetchPageV2("conf-access", CLOUD_ID, PAGE_ID))
        .willThrow(new AtlassianOAuthClient.ContentNotAccessibleException("없음/접근 불가"));

    assertThatThrownBy(() -> service().getPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("Confluence 일시 장애(AtlassianOAuthException)는 502 EXTERNAL_SERVICE_ERROR")
  void shouldReturn502WhenConfluenceFails() {
    givenUserWithToken(Instant.now().plus(1, ChronoUnit.HOURS));
    given(oauthClient.fetchPageV2("conf-access", CLOUD_ID, PAGE_ID))
        .willThrow(new AtlassianOAuthClient.AtlassianOAuthException("5xx"));

    assertThatThrownBy(() -> service().getPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
  }

  @Test
  @DisplayName("refresh 가 invalid_grant 면 401 UNAUTHORIZED (재로그인 필요)")
  void shouldReturn401WhenRefreshInvalidGrant() {
    givenUserWithToken(Instant.now().minus(1, ChronoUnit.MINUTES));
    given(oauthClient.refreshAccessToken("conf-refresh"))
        .willThrow(new AtlassianOAuthClient.InvalidGrantException("invalid_grant"));

    assertThatThrownBy(() -> service().getPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }
}
