package com.lina.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : auth-server 보안 설정. OAuth 진입점(login·callback)과 refresh(Body 의 refresh token 으로
 *           자체 검증)는 permitAll, logout 은 Bearer 필요(JwtAuthenticationFilter), /internal/** 는
 *           외부 차단(Feature 5/7 에서 내부 호출자 인증으로 대체), 나머지는 default deny.
 *           세션 미사용(stateless Bearer 계약 — api-spec §4-1), 미인증 응답은 공통 ErrorResponse 401.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 *   - 2026-06-12, 3단계 Feature 4 — refresh permitAll·logout Bearer 검증(JWT 필터)·/internal/** 차단·401 EntryPoint
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Security 6.3.x
 * --------------------------------------------------
 * </pre>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/callback", "/api/auth/refresh")
                    .permitAll()
                    // k8s liveness/readiness probe 용 (application.yml probes.enabled)
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // 외부 차단 — Feature 5/7 에서 내부 호출자 인증(NetworkPolicy/service auth)으로 대체
                    .requestMatchers("/internal/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exception ->
                exception.authenticationEntryPoint(
                    (request, response, authException) ->
                        writeUnauthorized(objectMapper, response)))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /** 미인증 응답을 공통 ErrorResponse 포맷으로 직렬화한다(401 UNAUTHORIZED — backend/CLAUDE.md §4). */
  private static void writeUnauthorized(ObjectMapper objectMapper, HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getWriter(),
        ErrorResponse.of(
            ErrorCode.UNAUTHORIZED.getHttpStatus(),
            ErrorCode.UNAUTHORIZED.getCode(),
            ErrorCode.UNAUTHORIZED.getDefaultMessage()));
  }
}
