package com.lina.bff.confluence.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lina.bff.confluence.dto.ConfluencePagePreviewResponse;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/** Feature P1 — auth-server 내부 미리보기 프록시 호출 계약 검증(실 auth-server 없이 WireMock 으로만). */
@WireMockTest
class AuthServerConfluenceClientTest {

  private static final String ENDPOINT = "/internal/auth/confluence/pages/preview";
  private static final String USER_ID = "712020:abc";
  private static final String PAGE_ID = "12345";

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  @Test
  @DisplayName("정상: pageId/userId 쿼리 + X-Internal-Api-Key 헤더로 호출하고 미리보기 DTO 를 매핑한다")
  void shouldFetchPreviewWithInternalKeyHeader() {
    wireMock.stubFor(
        get(urlPathEqualTo(ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "pageId": "12345",
                          "title": "S3 트러블슈팅 가이드",
                          "spaceName": "Cloud Control Center",
                          "authorName": "Platform Team",
                          "updatedAt": "2026-04-15T18:30:00+09:00",
                          "breadcrumbs": ["Cloud Control Center", "AWS", "S3", "S3 트러블슈팅 가이드"],
                          "pageUrl": "https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3",
                          "bodyViewValue": "<h1>S3</h1><p>권한 오류는...</p>"
                        }
                        """)));

    AuthServerConfluenceClient client = client("test-key");

    ConfluencePagePreviewResponse response = client.fetchPagePreview(USER_ID, PAGE_ID);

    assertThat(response.pageId()).isEqualTo(PAGE_ID);
    assertThat(response.title()).isEqualTo("S3 트러블슈팅 가이드");
    assertThat(response.spaceName()).isEqualTo("Cloud Control Center");
    assertThat(response.authorName()).isEqualTo("Platform Team");
    assertThat(response.updatedAt()).isEqualTo("2026-04-15T18:30:00+09:00");
    assertThat(response.breadcrumbs())
        .containsExactly("Cloud Control Center", "AWS", "S3", "S3 트러블슈팅 가이드");
    assertThat(response.pageUrl())
        .isEqualTo("https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3");
    assertThat(response.bodyViewValue()).isEqualTo("<h1>S3</h1><p>권한 오류는...</p>");
    wireMock.verify(
        getRequestedFor(urlPathEqualTo(ENDPOINT))
            .withQueryParam("pageId", equalTo(PAGE_ID))
            .withQueryParam("userId", equalTo(USER_ID))
            .withHeader("X-Internal-Api-Key", equalTo("test-key")));
  }

  @Test
  @DisplayName("내부 404 는 RESOURCE_NOT_FOUND 로 매핑한다(없음/접근 불가 비노출)")
  void shouldMapNotFoundTo404() {
    wireMock.stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(404)));

    AuthServerConfluenceClient client = client("test-key");

    assertThatThrownBy(() -> client.fetchPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("내부 401(재로그인 필요)은 UNAUTHORIZED 로 매핑한다")
  void shouldMapUnauthorizedTo401() {
    wireMock.stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(401)));

    AuthServerConfluenceClient client = client("test-key");

    assertThatThrownBy(() -> client.fetchPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("내부 5xx 는 EXTERNAL_SERVICE_ERROR(502) 로 매핑한다")
  void shouldMapServerErrorTo502() {
    wireMock.stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(aResponse().withStatus(503)));

    AuthServerConfluenceClient client = client("test-key");

    assertThatThrownBy(() -> client.fetchPagePreview(USER_ID, PAGE_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR);
  }

  private AuthServerConfluenceClient client(String internalApiKey) {
    RestClient restClient =
        RestClient.builder()
            .baseUrl(wireMock.baseUrl())
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
    AuthServerConfluenceClient client = new AuthServerConfluenceClient(restClient);
    ReflectionTestUtils.setField(client, "internalApiKey", internalApiKey);
    return client;
  }
}
