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
