package com.lina.auth.oauth;

import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProperties;
import com.lina.auth.jwt.JwtProvider;
import com.lina.auth.oauth.dto.AccessibleResource;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.AtlassianUserInfo;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.UserRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : OAuth Authorization Code Flow 오케스트레이션. login(authorize URL 구성·state 발급)과
 *           callback(state 검증 → AUTH-02 code 교환 → AUTH-04 cloudId → /me 사용자 정보 →
 *           AUTH-05 groups → users upsert·user_groups 교체·user_tokens 암호화 저장 →
 *           LINA 세션 JWT 발급)을 담당한다(docs/api-spec.md §4-1). 모든 실패(400/401/403)에서
 *           토큰을 발급하지 않으며 Confluence OAuth 토큰은 응답에 포함하지 않는다.
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
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

  private static final Logger log = LoggerFactory.getLogger(OAuthLoginService.class);

  private static final String ADMIN_MODE = "admin";
  private static final String AUDIENCE = "api.atlassian.com";
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final OAuthProperties properties;
  private final OAuthStateService stateService;
  private final AtlassianOAuthClient oauthClient;
  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final UserRepository userRepository;
  private final OAuthLoginPersistenceService persistenceService;

  /** login: returnTo(내부 경로만)/mode 를 state 로 보관하고 Atlassian authorize URL 을 구성한다(AUTH-01). */
  public URI buildAuthorizationRedirectUri(String mode, String returnTo) {
    String safeReturnTo = normalizeReturnTo(returnTo);
    String state = stateService.generate(mode, safeReturnTo);
    return UriComponentsBuilder.fromUriString(properties.getAuthorizationUri())
        .queryParam("audience", AUDIENCE)
        .queryParam("client_id", properties.getClientId())
        .queryParam("scope", properties.getScopes())
        .queryParam("redirect_uri", properties.getRedirectUri())
        .queryParam("state", state)
        .queryParam("response_type", "code")
        .queryParam("prompt", "consent")
        .build()
        .encode()
        .toUri();
  }

  /** callback: state 검증부터 JWT 발급까지 처리한다. 실패 시 400/401/403 — 모든 실패에서 토큰 미발급. */
  public LoginTokenResponse handleCallback(String code, String state) {
    OAuthStateService.StateData stateData = stateService.consume(state);

    AtlassianTokenResponse confluenceTokens = exchangeCode(code);
    AccessibleResource site = selectSite(fetchAccessibleResources(confluenceTokens.accessToken()));
    AtlassianUserInfo userInfo = fetchUserInfo(confluenceTokens.accessToken());

    Optional<User> existingUser = userRepository.findByUserId(userInfo.accountId());
    UserRole role = existingUser.map(User::getRole).orElse(UserRole.USER);
    if (ADMIN_MODE.equals(stateData.mode()) && role != UserRole.ADMIN) {
      throw new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다");
    }

    List<String> groupIds =
        fetchGroupIdsOrEmpty(confluenceTokens.accessToken(), site.id(), userInfo.accountId());

    Instant now = Instant.now();
    String accessJwt =
        jwtProvider.issueAccessToken(new JwtClaims(userInfo.accountId(), groupIds, role.name()));
    String refreshToken = jwtProvider.issueRefreshToken(userInfo.accountId());

    persistenceService.persistLoginState(
        existingUser,
        userInfo,
        accessJwt,
        refreshToken,
        groupIds,
        confluenceTokens,
        site.id(),
        now);

    return new LoginTokenResponse(accessJwt, refreshToken, accessTokenExpiresAt(now));
  }

  /** returnTo 는 내부 경로만 허용한다(오픈 리다이렉트 방지 — docs/api-spec.md §4-1). 미지정 시 '/'. */
  private static String normalizeReturnTo(String returnTo) {
    if (returnTo == null || returnTo.isBlank()) {
      return "/";
    }
    if (!returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "returnTo 는 내부 경로만 허용됩니다.");
    }
    return returnTo;
  }

  private AtlassianTokenResponse exchangeCode(String code) {
    try {
      return oauthClient.exchangeAuthorizationCode(code);
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "Confluence 인증에 실패했습니다.", e);
    }
  }

  private List<AccessibleResource> fetchAccessibleResources(String confluenceAccessToken) {
    try {
      return oauthClient.fetchAccessibleResources(confluenceAccessToken);
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "Confluence 인증에 실패했습니다.", e);
    }
  }

  private AtlassianUserInfo fetchUserInfo(String confluenceAccessToken) {
    try {
      return oauthClient.fetchUserInfo(confluenceAccessToken);
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "Confluence 인증에 실패했습니다.", e);
    }
  }

  /**
   * 사이트 선택 규칙: 공식 문서상 accessible-resources 반환 순서에 의미가 없으므로("최근 인가" 추론 금지) 단일 사이트만 자동 선택하고, 멀티 사이트는
   * 설정(site-url)과 일치하는 사이트만 허용한다 — 첫 번째 임의 선택 금지(current-plans Feature 3).
   */
  private AccessibleResource selectSite(List<AccessibleResource> resources) {
    if (resources == null || resources.isEmpty()) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "접근 가능한 Confluence 사이트가 없습니다.");
    }
    if (resources.size() == 1) {
      return resources.get(0);
    }
    String configuredSiteUrl = trimTrailingSlash(properties.getSiteUrl());
    if (configuredSiteUrl.isBlank()) {
      throw new BizException(
          ErrorCode.INTERNAL_ERROR, "멀티 사이트 계정은 사이트 선택 설정(CONFLUENCE_SITE_URL)이 필요합니다.");
    }
    return resources.stream()
        .filter(resource -> configuredSiteUrl.equals(trimTrailingSlash(resource.url())))
        .findFirst()
        .orElseThrow(
            () ->
                new BizException(
                    ErrorCode.INTERNAL_ERROR, "설정된 site-url 과 일치하는 Confluence 사이트가 없습니다."));
  }

  private static String trimTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * groups 조회 실패 시 로그인은 허용하되 빈 groups 로 발급한다 — 빈 배열은 질의 단계에서 user-level/공개 페이지만 매칭되는 fail-safe 동작
   * (docs/api-spec.md v2.6.0 ACL 정책: groups 빈 배열 허용).
   */
  private List<String> fetchGroupIdsOrEmpty(
      String confluenceAccessToken, String cloudId, String accountId) {
    try {
      return oauthClient.fetchGroupIds(confluenceAccessToken, cloudId, accountId);
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      log.warn("memberof(AUTH-05) 조회 실패 — 빈 groups 로 로그인을 허용한다. accountId={}", accountId, e);
      return List.of();
    }
  }

  /** access JWT 만료 시각(KST, ISO-8601 offset — docs/api-spec.md §4-1 expiresAt). */
  private String accessTokenExpiresAt(Instant issuedAt) {
    return OffsetDateTime.ofInstant(
            issuedAt.plusSeconds(jwtProperties.getAccessTokenTtlSeconds()), KST)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
