package com.lina.bff.confluence.client;

import com.lina.bff.confluence.dto.ConfluencePagePreviewResponse;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Confluence 미리보기 auth-server 내부 프록시(P2) 호출 client(Feature P1, api-spec §4-3).
 *           authServerRestClient 로 GET /internal/auth/confluence/pages/preview?pageId=&userId= 를
 *           호출하고 X-Internal-Api-Key 헤더를 주입한다. BFF 는 Confluence 토큰을 다루지 않고 매핑된 DTO 만
 *           수신한다. 내부 응답 상태(404/401/5xx)를 BizException 으로 매핑해 GlobalExceptionHandler 가
 *           공통 포맷으로 처리하게 한다.
 * 작성일 : 2026-06-18
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-18, 최초 작성, 4단계 Feature P1 — Confluence 미리보기 BFF 공개 endpoint
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x (RestClient 동기 I/O — Virtual Threads 위임)
 * --------------------------------------------------
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AuthServerConfluenceClient {

  private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

  private final RestClient authServerRestClient;

  @Value("${lina.internal.api-key:${RAG_INTERNAL_API_KEY:${INTERNAL_API_KEY:}}}")
  private String internalApiKey;

  public ConfluencePagePreviewResponse fetchPagePreview(String userId, String pageId) {
    try {
      return authServerRestClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/internal/auth/confluence/pages/preview")
                      .queryParam("pageId", pageId)
                      .queryParam("userId", userId)
                      .build())
          .header(INTERNAL_API_KEY_HEADER, internalApiKey == null ? "" : internalApiKey)
          .retrieve()
          .body(ConfluencePagePreviewResponse.class);
    } catch (HttpClientErrorException e) {
      int status = e.getStatusCode().value();
      if (status == 404) {
        throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Confluence 페이지 미리보기를 찾을 수 없습니다");
      }
      if (status == 401) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Confluence 재로그인이 필요합니다.");
      }
      throw new BizException(
          ErrorCode.EXTERNAL_SERVICE_ERROR, "Confluence 페이지 미리보기 조회에 실패했습니다.", e);
    } catch (RestClientException e) {
      throw new BizException(
          ErrorCode.EXTERNAL_SERVICE_ERROR, "Confluence 페이지 미리보기 조회에 실패했습니다.", e);
    }
  }
}
