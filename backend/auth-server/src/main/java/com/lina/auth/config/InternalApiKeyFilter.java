package com.lina.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

  public InternalApiKeyFilter(@Value("${lina.internal.api-key:}") String internalApiKey) {
    this.expectedKey =
        internalApiKey == null ? new byte[0] : internalApiKey.getBytes(StandardCharsets.UTF_8);
    if (this.expectedKey.length == 0) {
      log.warn("INTERNAL_API_KEY 미설정 — /internal/** 내부 인증은 모든 요청을 거부한다(fail-closed).");
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String presented = request.getHeader(HEADER_NAME);
    if (presented != null && matches(presented)) {
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }

  /** 키 미설정(빈 키)이면 항상 false. 비교는 상수시간(MessageDigest.isEqual)으로 타이밍 누설을 막는다. */
  private boolean matches(String presented) {
    return expectedKey.length > 0
        && MessageDigest.isEqual(expectedKey, presented.getBytes(StandardCharsets.UTF_8));
  }
}
