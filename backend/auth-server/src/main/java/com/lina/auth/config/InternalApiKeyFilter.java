package com.lina.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : /internal/** 내부 호출자(service auth) 인증 필터(Feature 5). X-Internal-Api-Key 헤더를
 *           env 주입 키(${INTERNAL_API_KEY})와 상수시간 비교해 일치하면 ROLE_INTERNAL 을 적재한다.
 *           키 미설정이면 어떤 요청도 인증하지 않는다(fail-closed) — 외부/FE/BFF 의 사용자 JWT 로는
 *           내부 권한을 얻을 수 없다. NetworkPolicy 와 병행하는 응용 계층 방어선(api-spec §2-5 보안 원칙).
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 5 — 내부 credential 조회 API 호출 주체 제한
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Security 6.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Internal-Api-Key";

  private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

  private final byte[] expectedKey;
  private final ObjectMapper objectMapper;

  public InternalApiKeyFilter(
      @Value("${lina.internal.api-key:}") String internalApiKey, ObjectMapper objectMapper) {
    this.expectedKey =
        internalApiKey == null ? new byte[0] : internalApiKey.getBytes(StandardCharsets.UTF_8);
    this.objectMapper = objectMapper;
    if (this.expectedKey.length == 0) {
      log.warn("INTERNAL_API_KEY 미설정 — /internal/** 내부 인증은 모든 요청을 거부한다(fail-closed).");
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/internal/")) {
      filterChain.doFilter(request, response);
      return;
    }

    log.debug(
        "내부 키 필터 진입 전 SecurityContext: {}",
        SecurityContextHolder.getContext().getAuthentication());
    log.debug(
        "내부 키 필터 진입 전 SecurityContext 인스턴스: {}",
        System.identityHashCode(SecurityContextHolder.getContext()));
    String presented = request.getHeader(HEADER_NAME);
    log.debug("내부 키 필터 진입: path={}, headerPresent={}", request.getRequestURI(), presented != null);
    if (presented != null && matches(presented)) {
      log.debug("내부 키 검증 성공");
      UsernamePasswordAuthenticationToken authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
      log.debug(
          "내부 인증 객체 생성: isAuthenticated={}, authorities={}",
          authentication.isAuthenticated(),
          authentication.getAuthorities());
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      log.debug("내부 키 필터 인증 후 SecurityContext 인스턴스: {}", System.identityHashCode(context));
      log.debug(
          "내부 키 필터 인증 후 SecurityContext 인증객체: {}",
          SecurityContextHolder.getContext().getAuthentication());
    } else {
      String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
      writeInternalError(
          response,
          authorization != null && authorization.startsWith("Bearer ")
              ? ErrorCode.FORBIDDEN
              : ErrorCode.UNAUTHORIZED);
      return;
    }
    filterChain.doFilter(request, response);
    log.debug(
        "내부 키 필터 종료 시 SecurityContext: {}",
        SecurityContextHolder.getContext().getAuthentication());
  }

  /** 키 미설정(빈 키)이면 항상 false. 비교는 상수시간(MessageDigest.isEqual)으로 타이밍 누설을 막는다. */
  private boolean matches(String presented) {
    return expectedKey.length > 0
        && MessageDigest.isEqual(expectedKey, presented.getBytes(StandardCharsets.UTF_8));
  }

  private void writeInternalError(HttpServletResponse response, ErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getWriter(),
        ErrorResponse.of(
            errorCode.getHttpStatus(), errorCode.getCode(), errorCode.getDefaultMessage()));
  }
}
