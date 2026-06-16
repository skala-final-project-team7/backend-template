package com.lina.auth.config;

import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
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
 * 작성목적 : Authorization: Bearer 의 LINA access JWT 를 검증해 SecurityContext 에 적재하는 필터.
 *           logout 등 인증 필요 엔드포인트의 호출자 식별(principal=userId)에 사용한다(Feature 4).
 *           검증 실패 시 인증을 적재하지 않고 통과시켜, 보호 경로에서는 EntryPoint 가 401 을 응답한다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 4 — 세션 관리
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Security 6.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtProvider jwtProvider;

  public JwtAuthenticationFilter(JwtProvider jwtProvider) {
    this.jwtProvider = jwtProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    log.debug("JWT 필터 진입: path={}, authHeaderPresent={}", request.getRequestURI(), header != null);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      try {
        JwtClaims claims = jwtProvider.verifyAccessToken(header.substring(BEARER_PREFIX.length()));
        log.debug("JWT 검증 성공: userId={}", claims.userId());
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                claims.userId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
        log.debug("JWT 인증 객체 isAuthenticated={}", authentication.isAuthenticated());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException e) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }
}
