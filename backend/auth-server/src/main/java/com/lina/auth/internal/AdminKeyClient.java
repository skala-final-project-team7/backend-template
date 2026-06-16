package com.lina.auth.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Atlassian Admin Key REST(`{siteUrl}/wiki/api/v2/admin-key`) 호출 전담 Client(Feature 6).
 *           admin-key 리소스는 OAuth2 앱 접근 불가(공식)라 admin API Token 기반 Basic auth 로 호출한다
 *           (Feature 0 게이트). base URL 은 site URL 가변이라 절대 URI 로 호출하며, 콘텐츠 조회용 OAuth
 *           게이트웨이(AtlassianOAuthClient)와 자격증명·URL 체계가 다르다(혼동 금지). 실패는
 *           AdminKeyException 으로 래핑하고 HTTP 매핑(502)은 Service 책임으로 둔다. 토큰은 로그 금지.
 * 작성일 : 2026-06-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-15, 최초 작성, 3단계 Feature 6 — Admin Key 내부 API
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x (RestClient 동기 I/O — Virtual Threads 위임)
 * --------------------------------------------------
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AdminKeyClient {

  private static final String ADMIN_KEY_PATH = "/wiki/api/v2/admin-key";

  /** Atlassian RestClient(base-url 없는 절대 URI 호출) — AtlassianOAuthClient 와 동일 빈 공유. */
  private final RestClient restClient;

  /** Admin Key 활성화 — `durationInMinutes` 동안 유효한 키를 발급하고 만료 시각(expirationTime)을 반환한다. */
  public String activate(
      String siteUrl, String adminEmail, String adminApiToken, int durationInMinutes) {
    try {
      AtlassianAdminKeyResponse response =
          restClient
              .post()
              .uri(siteUrl + ADMIN_KEY_PATH)
              .headers(headers -> headers.setBasicAuth(adminEmail, adminApiToken))
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("durationInMinutes", durationInMinutes))
              .retrieve()
              .body(AtlassianAdminKeyResponse.class);
      return response == null ? null : response.expirationTime();
    } catch (RestClientException e) {
      throw new AdminKeyException("Admin Key 활성화(Atlassian)에 실패했습니다.", e);
    }
  }

  /** Admin Key 비활성화 — 활성 키를 폐기한다(실측 `204`). 이미 비활성이어도 호출 자체는 안전. */
  public void deactivate(String siteUrl, String adminEmail, String adminApiToken) {
    try {
      restClient
          .delete()
          .uri(siteUrl + ADMIN_KEY_PATH)
          .headers(headers -> headers.setBasicAuth(adminEmail, adminApiToken))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new AdminKeyException("Admin Key 비활성화(Atlassian)에 실패했습니다.", e);
    }
  }

  /** Atlassian admin-key 응답(필요 필드만). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AtlassianAdminKeyResponse(String expirationTime) {}

  /** Atlassian admin-key 호출 실패 래퍼. 토큰 등 민감 값은 메시지에 싣지 않는다. */
  public static class AdminKeyException extends RuntimeException {

    public AdminKeyException(String message) {
      super(message);
    }

    public AdminKeyException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
