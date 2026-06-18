package com.lina.auth.oauth;

import com.lina.auth.oauth.dto.AccessibleResource;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.AtlassianUserInfo;
import com.lina.auth.oauth.dto.ConfluencePageV2Response;
import com.lina.auth.oauth.dto.ConfluenceSpaceV2Response;
import com.lina.auth.oauth.dto.GroupMembershipPage;
import com.lina.auth.oauth.dto.TokenExchangeRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
 *   - 2026-06-12, 3단계 Feature 5, AUTH-03 refresh_token 갱신 추가(invalid_grant 구분 — InvalidGrantException)
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

  /**
   * AUTH-03: refresh_token → 새 access/refresh 갱신(rotating — 이전 refresh 는 Atlassian 측 무효화).
   * invalid_grant 는 재로그인 필요 신호이므로 InvalidGrantException 으로 구분하고, 그 외 실패는 일시 장애로 본다(Feature 5).
   */
  public AtlassianTokenResponse refreshAccessToken(String refreshToken) {
    TokenExchangeRequest body =
        TokenExchangeRequest.builder()
            .grantType("refresh_token")
            .clientId(properties.getClientId())
            .clientSecret(properties.getClientSecret())
            .refreshToken(refreshToken)
            .build();
    try {
      return restClient
          .post()
          .uri(properties.getTokenUri())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(AtlassianTokenResponse.class);
    } catch (HttpClientErrorException e) {
      if (e.getResponseBodyAsString().contains("invalid_grant")) {
        throw new InvalidGrantException("Atlassian refresh token 이 무효화되었습니다(invalid_grant).");
      }
      throw new AtlassianOAuthException("Atlassian 토큰 갱신(AUTH-03)에 실패했습니다.", e);
    } catch (RestClientException e) {
      throw new AtlassianOAuthException("Atlassian 토큰 갱신(AUTH-03)에 실패했습니다.", e);
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

  /**
   * 미리보기(§4-3): Confluence v2 pages 조회(`body-format=view`). granular scope(`read:page:confluence`)
   * 전용 — 동일 토큰에 v1 content API 는 401("scope does not match")을 반환하므로 v2 를 사용한다. 페이지 없음(404)/접근
   * 불가(403)는 ContentNotAccessibleException 으로 구분하고(Service 가 404 로 통일), 그 외 실패는
   * AtlassianOAuthException 으로 래핑한다.
   */
  public ConfluencePageV2Response fetchPageV2(String accessToken, String cloudId, String pageId) {
    URI uri =
        UriComponentsBuilder.fromUriString(properties.getApiBaseUri())
            .path("/ex/confluence/{cloudId}/wiki/api/v2/pages/{pageId}")
            .queryParam("body-format", "view")
            .buildAndExpand(cloudId, pageId)
            .toUri();
    return getForPreview(uri, accessToken, ConfluencePageV2Response.class);
  }

  /** 미리보기 breadcrumbs/spaceName 구성용 v2 spaces 조회. 실패 분류는 fetchPageV2 와 동일. */
  public ConfluenceSpaceV2Response fetchSpaceV2(
      String accessToken, String cloudId, String spaceId) {
    URI uri =
        UriComponentsBuilder.fromUriString(properties.getApiBaseUri())
            .path("/ex/confluence/{cloudId}/wiki/api/v2/spaces/{spaceId}")
            .buildAndExpand(cloudId, spaceId)
            .toUri();
    return getForPreview(uri, accessToken, ConfluenceSpaceV2Response.class);
  }

  private <T> T getForPreview(URI uri, String accessToken, Class<T> responseType) {
    try {
      return restClient
          .get()
          .uri(uri)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .retrieve()
          .body(responseType);
    } catch (HttpClientErrorException e) {
      int status = e.getStatusCode().value();
      if (status == 404 || status == 403) {
        throw new ContentNotAccessibleException("Confluence 리소스를 찾을 수 없거나 접근 권한이 없습니다.");
      }
      throw new AtlassianOAuthException("Confluence(미리보기) 조회에 실패했습니다.", e);
    } catch (RestClientException e) {
      throw new AtlassianOAuthException("Confluence(미리보기) 조회에 실패했습니다.", e);
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

  /** AUTH-03 invalid_grant — refresh token 무효(회수/만료). 일시 장애가 아니라 재로그인이 필요한 상태다. */
  public static class InvalidGrantException extends AtlassianOAuthException {

    public InvalidGrantException(String message) {
      super(message);
    }
  }

  /**
   * 미리보기 대상 페이지가 없거나(404) 호출 사용자 권한 밖(403)일 때. Service 가 존재 비노출을 위해 둘 다 404 RESOURCE_NOT_FOUND 로
   * 통일한다.
   */
  public static class ContentNotAccessibleException extends AtlassianOAuthException {

    public ContentNotAccessibleException(String message) {
      super(message);
    }
  }
}
