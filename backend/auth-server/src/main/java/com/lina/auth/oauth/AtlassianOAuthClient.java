package com.lina.auth.oauth;

import com.lina.auth.oauth.dto.AccessibleResource;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.AtlassianUserInfo;
import com.lina.auth.oauth.dto.GroupMembershipPage;
import com.lina.auth.oauth.dto.TokenExchangeRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Atlassian OAuth 2.0 (3LO) 외부 계약 호출 전담 Client. AUTH-02(code 교환)·AUTH-04(cloudId)·
 *           AUTH-05(memberof groups 페이징 전량 수집)·user-info(/me) 를 호출한다
 *           (current-plans §외부 OAuth 계약 참조). 실패는 AtlassianOAuthException 으로 래핑하고
 *           HTTP 매핑은 Service 책임으로 둔다. 토큰 원문은 로그에 남기지 않는다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x (RestClient 동기 I/O — Virtual Threads 위임)
 * --------------------------------------------------
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AtlassianOAuthClient {

  /** AUTH-05 페이지 크기(Atlassian 기본 200). totalSize 초과분은 start 페이징으로 이어 받는다. */
  private static final int MEMBEROF_PAGE_LIMIT = 200;

  private final RestClient restClient;
  private final OAuthProperties properties;

  /** AUTH-02: authorization_code → Confluence access/refresh 교환. */
  public AtlassianTokenResponse exchangeAuthorizationCode(String code) {
    TokenExchangeRequest body =
        TokenExchangeRequest.builder()
            .grantType("authorization_code")
            .clientId(properties.getClientId())
            .clientSecret(properties.getClientSecret())
            .code(code)
            .redirectUri(properties.getRedirectUri())
            .build();
    try {
      return restClient
          .post()
          .uri(properties.getTokenUri())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(AtlassianTokenResponse.class);
    } catch (RestClientException e) {
      throw new AtlassianOAuthException("Atlassian 토큰 교환(AUTH-02)에 실패했습니다.", e);
    }
  }

  /** AUTH-04: 접근 가능 사이트(cloudId) 조회. 반환 순서에 의미 없음 — 선택 규칙은 Service 책임. */
  public List<AccessibleResource> fetchAccessibleResources(String accessToken) {
    try {
      return restClient
          .get()
          .uri(properties.getApiBaseUri() + "/oauth/token/accessible-resources")
          .headers(headers -> headers.setBearerAuth(accessToken))
          .retrieve()
          .body(new ParameterizedTypeReference<List<AccessibleResource>>() {});
    } catch (RestClientException e) {
      throw new AtlassianOAuthException("Atlassian accessible-resources(AUTH-04) 조회에 실패했습니다.", e);
    }
  }

  /** user-info(/me): accountId/email/name/picture 조회. */
  public AtlassianUserInfo fetchUserInfo(String accessToken) {
    try {
      return restClient
          .get()
          .uri(properties.getUserInfoUri())
          .headers(headers -> headers.setBearerAuth(accessToken))
          .retrieve()
          .body(AtlassianUserInfo.class);
    } catch (RestClientException e) {
      throw new AtlassianOAuthException("Atlassian 사용자 정보(/me) 조회에 실패했습니다.", e);
    }
  }

  /** AUTH-05: memberof groupId 전량 수집. totalSize 까지 start 페이징으로 이어 받는다(누락 금지). */
  public List<String> fetchGroupIds(String accessToken, String cloudId, String accountId) {
    List<String> groupIds = new ArrayList<>();
    int start = 0;
    while (true) {
      GroupMembershipPage page = fetchMemberofPage(accessToken, cloudId, accountId, start);
      if (page == null || page.results() == null || page.results().isEmpty()) {
        break;
      }
      page.results().forEach(group -> groupIds.add(group.id()));
      start += page.results().size();
      if (page.totalSize() == null || start >= page.totalSize()) {
        break;
      }
    }
    return groupIds;
  }

  private GroupMembershipPage fetchMemberofPage(
      String accessToken, String cloudId, String accountId, int start) {
    URI uri =
        UriComponentsBuilder.fromUriString(properties.getApiBaseUri())
            .path("/ex/confluence/{cloudId}/wiki/rest/api/user/memberof")
            .queryParam("accountId", accountId)
            .queryParam("start", start)
            .queryParam("limit", MEMBEROF_PAGE_LIMIT)
            .buildAndExpand(cloudId)
            .toUri();
    try {
      return restClient
          .get()
          .uri(uri)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .retrieve()
          .body(GroupMembershipPage.class);
    } catch (RestClientException e) {
      throw new AtlassianOAuthException("Confluence memberof(AUTH-05) 조회에 실패했습니다.", e);
    }
  }

  /** Atlassian 호출 실패 래퍼. 토큰 등 민감 값은 메시지에 싣지 않는다. */
  public static class AtlassianOAuthException extends RuntimeException {

    public AtlassianOAuthException(String message) {
      super(message);
    }

    public AtlassianOAuthException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
