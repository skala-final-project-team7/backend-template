package com.lina.auth.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Feature 6 — Atlassian Admin Key REST(`/wiki/api/v2/admin-key`) 외부 계약 검증. 실제 Atlassian 호출 없이
 * WireMock 으로만 검증한다. admin-key 는 OAuth2 앱 접근 불가라 **API Token Basic auth** 로 호출한다(Feature 0 게이트).
 */
@WireMockTest
class AdminKeyClientTest {

  private static final String ADMIN_EMAIL = "admin@example.com";
  private static final String ADMIN_API_TOKEN = "admin-api-token";
  private static final String ADMIN_KEY_PATH = "/wiki/api/v2/admin-key";

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  private static String expectedBasicAuth() {
    String raw = ADMIN_EMAIL + ":" + ADMIN_API_TOKEN;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("activate: Basic auth + durationInMinutes 를 보내고 expirationTime 을 매핑한다(200)")
  void shouldActivateAdminKey() {
    wireMock.stubFor(
        post(urlEqualTo(ADMIN_KEY_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "createdAt": "2026-06-15T11:00:00.000Z",
                          "expirationTime": "2026-06-15T12:00:00.000Z",
                          "active": true
                        }
                        """)));

    String expirationTime = client().activate(wireMock.baseUrl(), ADMIN_EMAIL, ADMIN_API_TOKEN, 60);

    assertThat(expirationTime).isEqualTo("2026-06-15T12:00:00.000Z");
    wireMock.verify(
        postRequestedFor(urlEqualTo(ADMIN_KEY_PATH))
            .withHeader("Authorization", equalTo(expectedBasicAuth()))
            .withRequestBody(equalToJson("{\"durationInMinutes\": 60}", true, false)));
  }

  @Test
  @DisplayName("deactivate: Basic auth 로 DELETE 호출(204)")
  void shouldDeactivateAdminKey() {
    wireMock.stubFor(delete(urlEqualTo(ADMIN_KEY_PATH)).willReturn(aResponse().withStatus(204)));

    client().deactivate(wireMock.baseUrl(), ADMIN_EMAIL, ADMIN_API_TOKEN);

    wireMock.verify(
        deleteRequestedFor(urlEqualTo(ADMIN_KEY_PATH))
            .withHeader("Authorization", equalTo(expectedBasicAuth())));
  }

  @Test
  @DisplayName("activate 가 5xx 면 AdminKeyException 으로 래핑한다")
  void shouldWrapActivateFailure() {
    wireMock.stubFor(post(urlEqualTo(ADMIN_KEY_PATH)).willReturn(aResponse().withStatus(503)));

    AdminKeyClient client = client();

    assertThatThrownBy(() -> client.activate(wireMock.baseUrl(), ADMIN_EMAIL, ADMIN_API_TOKEN, 60))
        .isInstanceOf(AdminKeyClient.AdminKeyException.class);
  }

  @Test
  @DisplayName("deactivate 가 5xx 면 AdminKeyException 으로 래핑한다")
  void shouldWrapDeactivateFailure() {
    wireMock.stubFor(delete(urlEqualTo(ADMIN_KEY_PATH)).willReturn(aResponse().withStatus(502)));

    AdminKeyClient client = client();

    assertThatThrownBy(() -> client.deactivate(wireMock.baseUrl(), ADMIN_EMAIL, ADMIN_API_TOKEN))
        .isInstanceOf(AdminKeyClient.AdminKeyException.class);
  }

  private AdminKeyClient client() {
    RestClient restClient =
        RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).build();
    return new AdminKeyClient(restClient);
  }
}
