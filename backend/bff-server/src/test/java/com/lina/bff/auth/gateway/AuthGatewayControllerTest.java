package com.lina.bff.auth.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.lina.bff.config.DemoSecurityConfig;
import com.lina.bff.security.BffJwtVerifier;
import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@WebMvcTest(controllers = AuthGatewayController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DemoSecurityConfig.class, AuthGatewayControllerTest.RestClientTestConfig.class})
@TestPropertySource(properties = "lina.auth-server.base-url=http://auth-server.test")
class AuthGatewayControllerTest {

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  @Autowired private MockMvc mockMvc;

  @MockitoBean
  private BffJwtVerifier jwtVerifier;

  @Test
  @DisplayName("GET /api/auth/login 을 auth-server 로 전달하고 302 Location 을 보존한다")
  void shouldProxyLoginRedirect() throws Exception {
    wireMock.stubFor(
        get(urlPathEqualTo("/api/auth/login"))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location", "https://auth.atlassian.com/authorize?state=state-123")));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/auth/login?mode=admin&returnTo=/admin"))
        .andExpect(status().isFound())
        .andExpect(
            header().string("Location", "https://auth.atlassian.com/authorize?state=state-123"));

    wireMock.verify(
        getRequestedFor(urlPathEqualTo("/api/auth/login"))
            .withQueryParam("mode", equalTo("admin"))
            .withQueryParam("returnTo", equalTo("/admin")));
  }

  @Test
  @DisplayName("POST /api/auth/refresh 의 body/header 를 auth-server 로 전달하고 응답 body 를 보존한다")
  void shouldProxyRefreshBodyAndHeaders() throws Exception {
    wireMock.stubFor(
        post(urlPathEqualTo("/api/auth/refresh"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "isSuccess": true,
                          "code": 200,
                          "message": "세션 갱신 성공",
                          "data": {
                            "accessToken": "new-access",
                            "refreshToken": "new-refresh",
                            "expiresAt": "2026-06-15T12:00:00+09:00"
                          }
                        }
                        """)));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/auth/refresh")
                .header("X-Request-Id", "req-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-1\"}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content().json("{\"isSuccess\":true,\"data\":{\"accessToken\":\"new-access\"}}"));

    wireMock.verify(
        postRequestedFor(urlPathEqualTo("/api/auth/refresh"))
            .withHeader("X-Request-Id", equalTo("req-1"))
            .withRequestBody(equalToJson("{\"refreshToken\":\"refresh-1\"}")));
  }

  @Test
  @DisplayName("auth-server 401 응답을 BFF 가 status/body 그대로 반환한다")
  void shouldPreserveAuthServerUnauthorizedResponse() throws Exception {
    wireMock.stubFor(
        post(urlPathEqualTo("/api/auth/logout"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "isSuccess": false,
                          "code": 401,
                          "errorCode": "UNAUTHORIZED",
                          "message": "인증이 필요합니다."
                        }
                        """)));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/auth/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"isSuccess\":false,\"errorCode\":\"UNAUTHORIZED\"}"));
  }

  @TestConfiguration
  static class RestClientTestConfig {

    @Bean
    RestClient authServerRestClient() {
      JdkClientHttpRequestFactory requestFactory =
          new JdkClientHttpRequestFactory(
              HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
      return RestClient.builder()
          .baseUrl(wireMock.baseUrl())
          .requestFactory(requestFactory)
          .build();
    }
  }
}
