package com.lina.bff.auth.gateway;

import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : FE 단일 진입점 유지를 위해 BFF /api/auth/** 요청을 auth-server 로 path-through proxy 한다.
 * 작성일 : 2026-06-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-15, 최초 작성, 3단계 Feature 1 — auth-server gateway 라우팅
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring MVC 6.1.x
 * --------------------------------------------------
 * </pre>
 */
@Controller
@RequestMapping("/api/auth")
public class AuthGatewayController {

  private static final Set<String> HOP_BY_HOP_HEADERS =
      Set.of(
          "connection",
          "keep-alive",
          "proxy-authenticate",
          "proxy-authorization",
          "te",
          "trailer",
          "transfer-encoding",
          "upgrade",
          "host",
          "content-length");

  private final RestClient authServerRestClient;
  private final String authServerBaseUrl;

  public AuthGatewayController(
      @Qualifier("authServerRestClient") RestClient authServerRestClient,
      @Value("${lina.auth-server.base-url:}") String authServerBaseUrl) {
    this.authServerRestClient = authServerRestClient;
    this.authServerBaseUrl = authServerBaseUrl;
  }

  @RequestMapping({"", "/**"})
  public ResponseEntity<?> proxy(
      HttpMethod method, HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    if (authServerBaseUrl == null || authServerBaseUrl.isBlank()) {
      ErrorCode errorCode = ErrorCode.EXTERNAL_SERVICE_ERROR;
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(
              ErrorResponse.of(
                  errorCode.getHttpStatus(),
                  errorCode.getCode(),
                  "auth-server base URL is not configured."));
    }

    try {
      return forward(method, request, body == null ? new byte[0] : body);
    } catch (RestClientResponseException exception) {
      return ResponseEntity.status(exception.getStatusCode())
          .headers(copyResponseHeaders(exception.getResponseHeaders()))
          .body(exception.getResponseBodyAsByteArray());
    } catch (RestClientException exception) {
      ErrorCode errorCode = ErrorCode.EXTERNAL_SERVICE_ERROR;
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(
              ErrorResponse.of(
                  errorCode.getHttpStatus(), errorCode.getCode(), "auth-server 호출에 실패했습니다."));
    }
  }

  private ResponseEntity<byte[]> forward(
      HttpMethod method, HttpServletRequest request, byte[] requestBody) {
    String targetPath = buildTargetPath(request);
    RestClient.RequestBodySpec requestSpec = authServerRestClient.method(method).uri(targetPath);
    copyRequestHeaders(request, requestSpec);

    if (requestBody.length > 0) {
      requestSpec.body(requestBody);
    }

    return requestSpec.exchange(
        (clientRequest, clientResponse) -> {
          HttpHeaders responseHeaders = copyResponseHeaders(clientResponse.getHeaders());
          byte[] responseBody = StreamUtils.copyToByteArray(clientResponse.getBody());
          return new ResponseEntity<>(
              responseBody, responseHeaders, clientResponse.getStatusCode());
        });
  }

  private String buildTargetPath(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    String requestUri = URI.create(request.getRequestURI()).getRawPath();
    String path =
        contextPath == null || contextPath.isBlank()
            ? requestUri
            : requestUri.substring(contextPath.length());
    String query = request.getQueryString();
    return query == null || query.isBlank() ? path : path + "?" + query;
  }

  private void copyRequestHeaders(
      HttpServletRequest request, RestClient.RequestBodySpec requestSpec) {
    requestSpec.headers(
        headers -> {
          var headerNames = request.getHeaderNames();
          while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (isHopByHopHeader(name)) {
              continue;
            }
            var values = request.getHeaders(name);
            while (values.hasMoreElements()) {
              headers.add(name, values.nextElement());
            }
          }
        });
  }

  private HttpHeaders copyResponseHeaders(HttpHeaders sourceHeaders) {
    HttpHeaders responseHeaders = new HttpHeaders();
    if (sourceHeaders == null) {
      return responseHeaders;
    }
    sourceHeaders.forEach(
        (name, values) -> {
          if (!isHopByHopHeader(name)) {
            responseHeaders.put(name, List.copyOf(values));
          }
        });
    return responseHeaders;
  }

  private boolean isHopByHopHeader(String name) {
    return HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT));
  }
}
