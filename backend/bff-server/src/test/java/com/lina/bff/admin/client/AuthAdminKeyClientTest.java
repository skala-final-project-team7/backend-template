package com.lina.bff.admin.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class AuthAdminKeyClientTest {

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  @Test
  @DisplayName("Admin Key activate 내부 API 를 adminUserId/jobId 로 호출한다")
  void shouldCallActivateInternalApi() {
    wireMock.stubFor(
        post(urlEqualTo("/internal/admin/key/activate")).willReturn(aResponse().withStatus(200)));

    AuthAdminKeyClient client = new AuthAdminKeyClient(buildRestClient());

    client.activate("admin-account-id", "job-1");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/internal/admin/key/activate"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "adminUserId": "admin-account-id",
                      "jobId": "job-1"
                    }
                    """,
                    true,
                    true)));
  }

  private RestClient buildRestClient() {
    return RestClient.builder()
        .baseUrl(wireMock.baseUrl())
        .requestFactory(new SimpleClientHttpRequestFactory())
        .build();
  }
}
