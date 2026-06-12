package com.lina.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProperties;
import com.lina.auth.jwt.JwtProvider;
import com.lina.auth.oauth.dto.AccessibleResource;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.AtlassianUserInfo;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserGroup;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.entity.UserToken;
import com.lina.auth.token.repository.UserGroupRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.auth.token.repository.UserTokenRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

  private static final String ACCOUNT_ID = "712020:abc";
  private static final String ADMIN_FORBIDDEN_MESSAGE = "관리자 권한이 없는 계정입니다";

  @Mock private OAuthStateService stateService;
  @Mock private AtlassianOAuthClient oauthClient;
  @Mock private JwtProvider jwtProvider;
  @Mock private UserRepository userRepository;
  @Mock private UserGroupRepository userGroupRepository;
  @Mock private UserTokenRepository userTokenRepository;

  @Captor private ArgumentCaptor<JwtClaims> claimsCaptor;
  @Captor private ArgumentCaptor<User> userCaptor;
  @Captor private ArgumentCaptor<UserToken> userTokenCaptor;
  @Captor private ArgumentCaptor<List<UserGroup>> userGroupsCaptor;

  private OAuthLoginService service() {
    return service("");
  }

  private OAuthLoginService service(String siteUrl) {
    OAuthProperties properties =
        new OAuthProperties(
            "client-id",
            "client-secret",
            "https://auth.atlassian.com/authorize",
            "https://auth.atlassian.com/oauth/token",
            "https://api.atlassian.com/me",
            "https://app.example.com/auth/callback",
            "read:confluence-user offline_access",
            "https://api.atlassian.com",
            siteUrl,
            600);
    JwtProperties jwtProperties =
        new JwtProperties("lina-auth-server", "unused-private", "unused-public", 3600, 1209600);
    OAuthLoginPersistenceService persistenceService =
        new OAuthLoginPersistenceService(userRepository, userGroupRepository, userTokenRepository);
    return new OAuthLoginService(
        properties,
        stateService,
        oauthClient,
        jwtProvider,
        jwtProperties,
        userRepository,
        persistenceService);
  }

  // --- login 리다이렉트 ---

  @Test
  @DisplayName(
      "login: authorize URL 에 audience/client_id/scope/redirect_uri/state/response_type/prompt 를 싣는다")
  void shouldBuildAuthorizeRedirectWithRequiredParams() {
    given(stateService.generate("admin", "/admin/dashboard")).willReturn("state-123");

    URI uri = service().buildAuthorizationRedirectUri("admin", "/admin/dashboard");

    assertThat(uri.toString()).startsWith("https://auth.atlassian.com/authorize?");
    String decodedQuery = uri.getQuery();
    assertThat(decodedQuery)
        .contains("audience=api.atlassian.com")
        .contains("client_id=client-id")
        .contains("scope=read:confluence-user offline_access")
        .contains("redirect_uri=https://app.example.com/auth/callback")
        .contains("state=state-123")
        .contains("response_type=code")
        .contains("prompt=consent");
  }

  @Test
  @DisplayName("login: returnTo 미지정 시 내부 기본 경로 '/' 로 state 를 발급한다")
  void shouldDefaultReturnToInternalRoot() {
    given(stateService.generate(null, "/")).willReturn("state-123");

    URI uri = service().buildAuthorizationRedirectUri(null, null);

    assertThat(uri.getQuery()).contains("state=state-123");
  }

  @Test
  @DisplayName("login: 외부 URL·프로토콜 상대 returnTo 는 INVALID_REQUEST 로 거부한다(오픈 리다이렉트 방지)")
  void shouldRejectExternalReturnTo() {
    OAuthLoginService service = service();

    for (String returnTo : List.of("https://evil.com", "//evil.com", "javascript:alert(1)")) {
      assertThatThrownBy(() -> service.buildAuthorizationRedirectUri(null, returnTo))
          .isInstanceOf(BizException.class)
          .extracting(e -> ((BizException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
    verify(stateService, never()).generate(any(), any());
  }

  // --- callback 성공 ---

  @Test
  @DisplayName("callback: 신규 사용자는 role=USER 로 INSERT 하고 groups 교체·Confluence 토큰 저장·JWT 발급한다")
  void shouldLoginNewUserWithUserRole() {
    stubHappyExternalCalls(List.of(resource("cloud-1", "https://team.atlassian.net")));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());
    given(oauthClient.fetchGroupIds("conf-access", "cloud-1", ACCOUNT_ID))
        .willReturn(List.of("g-1", "g-2"));
    given(jwtProvider.issueAccessToken(any())).willReturn("lina-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("lina-refresh");

    LoginTokenResponse response = service().handleCallback("auth-code-1", "state-123");

    assertThat(response.accessToken()).isEqualTo("lina-access");
    assertThat(response.refreshToken()).isEqualTo("lina-refresh");
    assertThat(OffsetDateTime.parse(response.expiresAt()).getOffset().getId()).isEqualTo("+09:00");

    verify(jwtProvider).issueAccessToken(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
        .isEqualTo(new JwtClaims(ACCOUNT_ID, List.of("g-1", "g-2"), "USER"));

    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getUserId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedUser.getEmail()).isEqualTo("dayeon@example.com");
    assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
    assertThat(savedUser.getAccessToken()).isEqualTo("lina-access");
    assertThat(savedUser.getRefreshToken()).isEqualTo("lina-refresh");
    assertThat(savedUser.getLastLoginAt()).isNotNull();

    verify(userGroupRepository).deleteByUserKey(savedUser.getUserKey());
    verify(userGroupRepository).saveAll(userGroupsCaptor.capture());
    assertThat(userGroupsCaptor.getValue())
        .extracting(UserGroup::getGroupId)
        .containsExactlyInAnyOrder("g-1", "g-2");

    verify(userTokenRepository).save(userTokenCaptor.capture());
    UserToken savedToken = userTokenCaptor.getValue();
    assertThat(savedToken.getUserKey()).isEqualTo(savedUser.getUserKey());
    assertThat(savedToken.getConfluenceAccessToken()).isEqualTo("conf-access");
    assertThat(savedToken.getConfluenceRefreshToken()).isEqualTo("conf-refresh");
    assertThat(savedToken.getCloudId()).isEqualTo("cloud-1");
    assertThat(savedToken.getAccessTokenExpiresAt()).isNotNull();
  }

  @Test
  @DisplayName("callback: 응답에 Confluence OAuth 토큰을 포함하지 않는다(FE 미노출)")
  void shouldNotExposeConfluenceTokensInResponse() {
    stubHappyExternalCalls(List.of(resource("cloud-1", "https://team.atlassian.net")));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());
    given(oauthClient.fetchGroupIds(anyString(), anyString(), anyString())).willReturn(List.of());
    given(jwtProvider.issueAccessToken(any())).willReturn("lina-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("lina-refresh");

    LoginTokenResponse response = service().handleCallback("auth-code-1", "state-123");

    assertThat(List.of(response.accessToken(), response.refreshToken(), response.expiresAt()))
        .noneMatch(value -> value.contains("conf-access") || value.contains("conf-refresh"));
  }

  @Test
  @DisplayName("callback: 기존 사용자는 role 을 유지한 채 프로필·토큰만 갱신하고 기존 멤버십을 교체한다")
  void shouldPreserveExistingRoleOnLogin() {
    User existing =
        User.builder()
            .userId(ACCOUNT_ID)
            .email("dayeon@example.com")
            .name("이전이름")
            .role(UserRole.ADMIN)
            .accessToken("old-access")
            .build();
    stubHappyExternalCalls(List.of(resource("cloud-1", "https://team.atlassian.net")));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(existing));
    given(oauthClient.fetchGroupIds("conf-access", "cloud-1", ACCOUNT_ID))
        .willReturn(List.of("g-1"));
    given(jwtProvider.issueAccessToken(any())).willReturn("lina-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("lina-refresh");

    service().handleCallback("auth-code-1", "state-123");

    verify(jwtProvider).issueAccessToken(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue().role()).isEqualTo("ADMIN");
    verify(userRepository).save(existing);
    assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(existing.getName()).isEqualTo("이다연");
    assertThat(existing.getAccessToken()).isEqualTo("lina-access");
    assertThat(existing.getRefreshToken()).isEqualTo("lina-refresh");
    verify(userGroupRepository).deleteByUserKey(existing.getUserKey());
  }

  @Test
  @DisplayName("callback: 기존 user_tokens 가 있으면 rotate 로 덮어쓴다(이전 값 미보존)")
  void shouldRotateExistingConfluenceTokens() {
    User existing =
        User.builder()
            .userId(ACCOUNT_ID)
            .email("dayeon@example.com")
            .role(UserRole.USER)
            .accessToken("old-access")
            .build();
    UserToken existingToken =
        UserToken.builder()
            .userKey(existing.getUserKey())
            .confluenceAccessToken("old-conf-access")
            .confluenceRefreshToken("old-conf-refresh")
            .cloudId("cloud-1")
            .accessTokenExpiresAt(OffsetDateTime.now().toInstant())
            .build();
    stubHappyExternalCalls(List.of(resource("cloud-1", "https://team.atlassian.net")));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(existing));
    given(userTokenRepository.findById(existing.getUserKey()))
        .willReturn(Optional.of(existingToken));
    given(oauthClient.fetchGroupIds(anyString(), anyString(), anyString())).willReturn(List.of());
    given(jwtProvider.issueAccessToken(any())).willReturn("lina-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("lina-refresh");

    service().handleCallback("auth-code-1", "state-123");

    assertThat(existingToken.getConfluenceAccessToken()).isEqualTo("conf-access");
    assertThat(existingToken.getConfluenceRefreshToken()).isEqualTo("conf-refresh");
    verify(userTokenRepository, never()).save(any());
  }

  // --- callback 실패 ---

  @Test
  @DisplayName("callback: mode=admin 인데 role != ADMIN 이면 403 — 토큰 미발급·영속 없음")
  void shouldRejectAdminModeForNonAdmin() {
    given(stateService.consume("state-123"))
        .willReturn(new OAuthStateService.StateData("admin", "/"));
    given(oauthClient.exchangeAuthorizationCode("auth-code-1"))
        .willReturn(new AtlassianTokenResponse("conf-access", "conf-refresh", 3600, "scope"));
    given(oauthClient.fetchAccessibleResources("conf-access"))
        .willReturn(List.of(resource("cloud-1", "https://team.atlassian.net")));
    given(oauthClient.fetchUserInfo("conf-access"))
        .willReturn(
            new AtlassianUserInfo(
                ACCOUNT_ID, "dayeon@example.com", "이다연", "https://example.com/profile.png"));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());

    OAuthLoginService service = service();

    assertThatThrownBy(() -> service.handleCallback("auth-code-1", "state-123"))
        .isInstanceOf(BizException.class)
        .hasMessage(ADMIN_FORBIDDEN_MESSAGE)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);

    verify(jwtProvider, never()).issueAccessToken(any());
    verify(jwtProvider, never()).issueRefreshToken(any());
    verify(userRepository, never()).save(any());
    verify(userTokenRepository, never()).save(any());
    verify(userGroupRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("callback: code 교환 실패는 401 UNAUTHORIZED 로 매핑하고 아무것도 저장하지 않는다")
  void shouldMapTokenExchangeFailureToUnauthorized() {
    given(stateService.consume("state-123")).willReturn(new OAuthStateService.StateData(null, "/"));
    given(oauthClient.exchangeAuthorizationCode("bad-code"))
        .willThrow(new AtlassianOAuthClient.AtlassianOAuthException("invalid_grant"));

    OAuthLoginService service = service();

    assertThatThrownBy(() -> service.handleCallback("bad-code", "state-123"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);

    verify(userRepository, never()).save(any());
    verify(userTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("callback: memberof 조회 실패 시 로그인은 허용하되 빈 groups 로 JWT 를 발급한다(질의는 ACL 에서 차단)")
  void shouldAllowLoginWithEmptyGroupsWhenMemberofFails() {
    stubHappyExternalCalls(List.of(resource("cloud-1", "https://team.atlassian.net")));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());
    given(oauthClient.fetchGroupIds("conf-access", "cloud-1", ACCOUNT_ID))
        .willThrow(new AtlassianOAuthClient.AtlassianOAuthException("memberof 5xx"));
    given(jwtProvider.issueAccessToken(any())).willReturn("lina-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("lina-refresh");

    LoginTokenResponse response = service().handleCallback("auth-code-1", "state-123");

    assertThat(response.accessToken()).isEqualTo("lina-access");
    verify(jwtProvider).issueAccessToken(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue().groups()).isEmpty();
  }

  @Test
  @DisplayName("callback: 접근 가능한 사이트가 없으면 401 UNAUTHORIZED")
  void shouldRejectWhenNoAccessibleResource() {
    given(stateService.consume("state-123")).willReturn(new OAuthStateService.StateData(null, "/"));
    given(oauthClient.exchangeAuthorizationCode("auth-code-1"))
        .willReturn(new AtlassianTokenResponse("conf-access", "conf-refresh", 3600, "scope"));
    given(oauthClient.fetchAccessibleResources("conf-access")).willReturn(List.of());

    OAuthLoginService service = service();

    assertThatThrownBy(() -> service.handleCallback("auth-code-1", "state-123"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("callback: 멀티 사이트는 설정된 site-url 과 일치하는 사이트를 선택한다(첫 번째 임의 선택 금지)")
  void shouldSelectConfiguredSiteAmongMultipleResources() {
    stubHappyExternalCalls(
        List.of(
            resource("cloud-1", "https://team1.atlassian.net"),
            resource("cloud-2", "https://team2.atlassian.net")));
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());
    given(oauthClient.fetchGroupIds("conf-access", "cloud-2", ACCOUNT_ID)).willReturn(List.of());
    given(jwtProvider.issueAccessToken(any())).willReturn("lina-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("lina-refresh");

    service("https://team2.atlassian.net").handleCallback("auth-code-1", "state-123");

    verify(userTokenRepository).save(userTokenCaptor.capture());
    assertThat(userTokenCaptor.getValue().getCloudId()).isEqualTo("cloud-2");
  }

  @Test
  @DisplayName("callback: 멀티 사이트인데 site-url 미설정/불일치면 INTERNAL_ERROR(임의 선택 금지)")
  void shouldFailWhenMultipleResourcesWithoutSiteUrlConfig() {
    given(stateService.consume("state-123")).willReturn(new OAuthStateService.StateData(null, "/"));
    given(oauthClient.exchangeAuthorizationCode("auth-code-1"))
        .willReturn(new AtlassianTokenResponse("conf-access", "conf-refresh", 3600, "scope"));
    given(oauthClient.fetchAccessibleResources("conf-access"))
        .willReturn(
            List.of(
                resource("cloud-1", "https://team1.atlassian.net"),
                resource("cloud-2", "https://team2.atlassian.net")));

    OAuthLoginService service = service();

    assertThatThrownBy(() -> service.handleCallback("auth-code-1", "state-123"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  // --- helpers ---

  private void stubHappyExternalCalls(List<AccessibleResource> resources) {
    given(stateService.consume("state-123")).willReturn(new OAuthStateService.StateData(null, "/"));
    given(oauthClient.exchangeAuthorizationCode("auth-code-1"))
        .willReturn(new AtlassianTokenResponse("conf-access", "conf-refresh", 3600, "scope"));
    given(oauthClient.fetchAccessibleResources("conf-access")).willReturn(resources);
    given(oauthClient.fetchUserInfo("conf-access"))
        .willReturn(
            new AtlassianUserInfo(
                ACCOUNT_ID, "dayeon@example.com", "이다연", "https://example.com/profile.png"));
  }

  private static AccessibleResource resource(String id, String url) {
    return new AccessibleResource(id, "site-name", url);
  }
}
