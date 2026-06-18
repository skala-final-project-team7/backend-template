package com.lina.auth.oauth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lina.auth.oauth.dto.AccessibleResource;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.AtlassianUserInfo;
import com.lina.auth.oauth.dto.ConfluencePageV2Response;
import com.lina.auth.oauth.dto.ConfluenceSpaceV2Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** AUTH-02/04/05·user-info 외부 계약 검증. 실제 Atlassian 호출 없이 WireMock 으로만 검증한다. */
@WireMockTest
class AtlassianOAuthClientTest {

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  @Test
  @DisplayName("AUTH-02: authorization_code 교환 요청 body 를 계약대로 보내고 토큰 응답을 매핑한다")
  void shouldExchangeAuthorizationCode() {
    wireMock.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "conf-access",
                          "refresh_token": "conf-refresh",
                          "expires_in": 3600,
                          "scope": "read:confluence-user offline_access"
                        }
                        """)));

    AtlassianTokenResponse response = client().exchangeAuthorizationCode("auth-code-1");

    assertThat(response.accessToken()).isEqualTo("conf-access");
    assertThat(response.refreshToken()).isEqualTo("conf-refresh");
    assertThat(response.expiresIn()).isEqualTo(3600);
    wireMock.verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "grant_type": "authorization_code",
                      "client_id": "client-id",
                      "client_secret": "client-secret",
                      "code": "auth-code-1",
                      "redirect_uri": "https://app.example.com/auth/callback"
                    }
                    """,
                    true,
                    false)));
  }

  @Test
  @DisplayName("AUTH-04: accessible-resources 를 Bearer 로 조회해 cloudId/url 을 매핑한다")
  void shouldFetchAccessibleResources() {
    wireMock.stubFor(
        get(urlEqualTo("/oauth/token/accessible-resources"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {
                            "id": "cloud-1",
                            "name": "team-site",
                            "url": "https://team.atlassian.net",
                            "scopes": ["read:confluence-user"],
                            "avatarUrl": "https://example.com/avatar.png"
                          }
                        ]
                        """)));

    List<AccessibleResource> resources = client().fetchAccessibleResources("conf-access");

    assertThat(resources).hasSize(1);
    assertThat(resources.get(0).id()).isEqualTo("cloud-1");
    assertThat(resources.get(0).url()).isEqualTo("https://team.atlassian.net");
    wireMock.verify(
        getRequestedFor(urlEqualTo("/oauth/token/accessible-resources"))
            .withHeader("Authorization", equalTo("Bearer conf-access")));
  }

  @Test
  @DisplayName("user-info(/me)를 Bearer 로 조회해 accountId/email/name/picture 를 매핑한다")
  void shouldFetchUserInfo() {
    wireMock.stubFor(
        get(urlEqualTo("/me"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "account_id": "712020:abc",
                          "email": "dayeon@example.com",
                          "name": "이다연",
                          "picture": "https://example.com/profile.png",
                          "account_type": "atlassian"
                        }
                        """)));

    AtlassianUserInfo userInfo = client().fetchUserInfo("conf-access");

    assertThat(userInfo.accountId()).isEqualTo("712020:abc");
    assertThat(userInfo.email()).isEqualTo("dayeon@example.com");
    assertThat(userInfo.name()).isEqualTo("이다연");
    assertThat(userInfo.picture()).isEqualTo("https://example.com/profile.png");
    wireMock.verify(
        getRequestedFor(urlEqualTo("/me"))
            .withHeader("Authorization", equalTo("Bearer conf-access")));
  }

  @Test
  @DisplayName("AUTH-05: memberof 를 totalSize 까지 start 페이징으로 전량 수집해 groupId 만 반환한다")
  void shouldFetchAllGroupIdsWithPagination() {
    String memberofPath = "/ex/confluence/cloud-1/wiki/rest/api/user/memberof";
    wireMock.stubFor(
        get(urlPathEqualTo(memberofPath))
            .withQueryParam("accountId", equalTo("712020:abc"))
            .withQueryParam("start", equalTo("0"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "results": [
                            {"type": "group", "name": "group-one", "id": "g-1"},
                            {"type": "group", "name": "group-two", "id": "g-2"}
                          ],
                          "start": 0,
                          "limit": 2,
                          "size": 2,
                          "totalSize": 3
                        }
                        """)));
    wireMock.stubFor(
        get(urlPathEqualTo(memberofPath))
            .withQueryParam("accountId", equalTo("712020:abc"))
            .withQueryParam("start", equalTo("2"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "results": [
                            {"type": "group", "name": "group-three", "id": "g-3"}
                          ],
                          "start": 2,
                          "limit": 2,
                          "size": 1,
                          "totalSize": 3
                        }
                        """)));

    List<String> groupIds = client().fetchGroupIds("conf-access", "cloud-1", "712020:abc");

    assertThat(groupIds).containsExactly("g-1", "g-2", "g-3");
    wireMock.verify(
        getRequestedFor(urlPathEqualTo(memberofPath))
            .withQueryParam("start", equalTo("0"))
            .withHeader("Authorization", equalTo("Bearer conf-access")));
    wireMock.verify(
        getRequestedFor(urlPathEqualTo(memberofPath)).withQueryParam("start", equalTo("2")));
  }

  @Test
  @DisplayName("Atlassian 이 오류 응답을 주면 AtlassianOAuthException 으로 래핑한다")
  void shouldWrapAtlassianErrorResponse() {
    wireMock.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"invalid_grant\"}")));

    AtlassianOAuthClient client = client();

    assertThatThrownBy(() -> client.exchangeAuthorizationCode("bad-code"))
        .isInstanceOf(AtlassianOAuthClient.AtlassianOAuthException.class);
  }

  // --- AUTH-03: refresh_token 갱신 (Feature 5) ---

  @Test
  @DisplayName("AUTH-03: refresh_token 갱신 요청 body 를 계약대로 보내고(code/redirect_uri 미포함) 토큰 응답을 매핑한다")
  void shouldRefreshAccessToken() {
    wireMock.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "access_token": "rotated-access",
                          "refresh_token": "rotated-refresh",
                          "expires_in": 3600,
                          "scope": "read:confluence-user offline_access"
                        }
                        """)));

    AtlassianTokenResponse response = client().refreshAccessToken("conf-refresh-1");

    assertThat(response.accessToken()).isEqualTo("rotated-access");
    assertThat(response.refreshToken()).isEqualTo("rotated-refresh");
    wireMock.verify(
        postRequestedFor(urlEqualTo("/oauth/token"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "grant_type": "refresh_token",
                      "client_id": "client-id",
                      "client_secret": "client-secret",
                      "refresh_token": "conf-refresh-1"
                    }
                    """,
                    true,
                    false)));
  }

  @Test
  @DisplayName("AUTH-03: invalid_grant 응답은 InvalidGrantException 으로 구분한다(재로그인 필요)")
  void shouldThrowInvalidGrantOnRefreshRejection() {
    wireMock.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"invalid_grant\"}")));

    AtlassianOAuthClient client = client();

    assertThatThrownBy(() -> client.refreshAccessToken("revoked-refresh"))
        .isInstanceOf(AtlassianOAuthClient.InvalidGrantException.class);
  }

  @Test
  @DisplayName("AUTH-03: 5xx 일시 장애는 일반 AtlassianOAuthException 으로 래핑한다(invalid_grant 아님)")
  void shouldWrapTransientRefreshFailure() {
    wireMock.stubFor(
        post(urlEqualTo("/oauth/token"))
            .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

    AtlassianOAuthClient client = client();

    assertThatThrownBy(() -> client.refreshAccessToken("conf-refresh-1"))
        .isInstanceOf(AtlassianOAuthClient.AtlassianOAuthException.class)
        .isNotInstanceOf(AtlassianOAuthClient.InvalidGrantException.class);
  }

  // --- 미리보기(§4-3 Feature P2): Confluence v2 pages/spaces 조회 ---

  @Test
  @DisplayName(
      "미리보기: v2 pages 를 body-format=view·Bearer 로 조회하고 title/body.view/version/_links 를 매핑한다")
  void shouldFetchPageV2() {
    String pagePath = "/ex/confluence/cloud-1/wiki/api/v2/pages/12345";
    wireMock.stubFor(
        get(urlPathEqualTo(pagePath))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": "12345",
                          "title": "S3 트러블슈팅 가이드",
                          "spaceId": "2850818",
                          "version": {"createdAt": "2026-04-15T09:30:00.000Z"},
                          "body": {"view": {"value": "<h1>S3</h1><p>권한 오류는...</p>"}},
                          "_links": {"base": "https://team.atlassian.net/wiki",
                            "webui": "/spaces/CCC/pages/12345/S3"}
                        }
                        """)));

    ConfluencePageV2Response page = client().fetchPageV2("conf-access", "cloud-1", "12345");

    assertThat(page.title()).isEqualTo("S3 트러블슈팅 가이드");
    assertThat(page.spaceId()).isEqualTo("2850818");
    assertThat(page.version().createdAt()).isEqualTo("2026-04-15T09:30:00.000Z");
    assertThat(page.body().view().value()).contains("<h1>S3</h1>");
    assertThat(page.links().base()).isEqualTo("https://team.atlassian.net/wiki");
    assertThat(page.links().webui()).isEqualTo("/spaces/CCC/pages/12345/S3");
    wireMock.verify(
        getRequestedFor(urlPathEqualTo(pagePath))
            .withQueryParam("body-format", equalTo("view"))
            .withHeader("Authorization", equalTo("Bearer conf-access")));
  }

  @Test
  @DisplayName("미리보기: v2 spaces 를 조회해 name 을 매핑한다")
  void shouldFetchSpaceV2() {
    wireMock.stubFor(
        get(urlPathEqualTo("/ex/confluence/cloud-1/wiki/api/v2/spaces/2850818"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"id": "2850818", "key": "CLOUD", "name": "Cloud Platform"}
                        """)));

    ConfluenceSpaceV2Response space = client().fetchSpaceV2("conf-access", "cloud-1", "2850818");

    assertThat(space.name()).isEqualTo("Cloud Platform");
  }

  @Test
  @DisplayName("미리보기: v2 404(없음)는 ContentNotAccessibleException 으로 구분한다")
  void shouldThrowContentNotAccessibleOnNotFound() {
    wireMock.stubFor(
        get(urlPathEqualTo("/ex/confluence/cloud-1/wiki/api/v2/pages/404"))
            .willReturn(aResponse().withStatus(404)));

    AtlassianOAuthClient client = client();

    assertThatThrownBy(() -> client.fetchPageV2("conf-access", "cloud-1", "404"))
        .isInstanceOf(AtlassianOAuthClient.ContentNotAccessibleException.class);
  }

  @Test
  @DisplayName("미리보기: v2 403(접근 불가)도 ContentNotAccessibleException 으로 구분한다(존재 비노출)")
  void shouldThrowContentNotAccessibleOnForbidden() {
    wireMock.stubFor(
        get(urlPathEqualTo("/ex/confluence/cloud-1/wiki/api/v2/pages/403"))
            .willReturn(aResponse().withStatus(403)));

    AtlassianOAuthClient client = client();

    assertThatThrownBy(() -> client.fetchPageV2("conf-access", "cloud-1", "403"))
        .isInstanceOf(AtlassianOAuthClient.ContentNotAccessibleException.class);
  }

  @Test
  @DisplayName("미리보기: v2 5xx 일시 장애는 일반 AtlassianOAuthException(ContentNotAccessible 아님)")
  void shouldWrapServerErrorOnPreview() {
    wireMock.stubFor(
        get(urlPathEqualTo("/ex/confluence/cloud-1/wiki/api/v2/pages/500"))
            .willReturn(aResponse().withStatus(500)));

    AtlassianOAuthClient client = client();

    assertThatThrownBy(() -> client.fetchPageV2("conf-access", "cloud-1", "500"))
        .isInstanceOf(AtlassianOAuthClient.AtlassianOAuthException.class)
        .isNotInstanceOf(AtlassianOAuthClient.ContentNotAccessibleException.class);
  }

  private AtlassianOAuthClient client() {
    String baseUrl = wireMock.baseUrl();
    OAuthProperties properties =
        new OAuthProperties(
            "client-id",
            "client-secret",
            baseUrl + "/authorize",
            baseUrl + "/oauth/token",
            baseUrl + "/me",
            "https://app.example.com/auth/callback",
            "read:confluence-user offline_access",
            baseUrl,
            "",
            600);
    RestClient restClient =
        RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).build();
    return new AtlassianOAuthClient(restClient, properties);
  }
}
