package com.lina.auth.config;

import tools.jackson.databind.ObjectMapper;
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
 *           자체 검증)는 permitAll, logout 은 Bearer 필요(JwtAuthenticationFilter),
 *           /internal/auth/** 는 내부 키 인증(InternalApiKeyFilter — ROLE_INTERNAL)만 허용,
 *           나머지 /internal/** 는 외부 차단(Feature 6/7 에서 확장), 그 외는 default deny.
 *           세션 미사용(stateless Bearer 계약 — api-spec §4-1), 미인증 응답은 공통 ErrorResponse 401,
 *           권한 부족(사용자 JWT 로 내부 API 접근 등)은 공통 ErrorResponse 403.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 *   - 2026-06-12, 3단계 Feature 4 — refresh permitAll·logout Bearer 검증(JWT 필터)·/internal/** 차단·401 EntryPoint
 *   - 2026-06-12, 3단계 Feature 5 — /internal/auth/** 내부 키 인증(ROLE_INTERNAL)·403 AccessDeniedHandler
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
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      InternalApiKeyFilter internalApiKeyFilter,
      ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .anonymous(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/callback", "/api/auth/refresh")
                    .permitAll()
                    // k8s liveness/readiness probe 용 (application.yml probes.enabled)
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    // 모니터링 수집용 endpoint (metrics/prometheus) 공개
                    .requestMatchers(
                        "/actuator/info",
                        "/actuator/metrics",
                        "/actuator/metrics/**",
                        "/actuator/prometheus")
                    .permitAll()
                    // 내부 API 호출은 InternalApiKeyFilter(=경로 게이트)에서 401/403 처리한다.
                    .requestMatchers("/internal/auth/**", "/internal/admin/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(
                        (request, response, authException) ->
                            writeError(objectMapper, response, ErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            writeError(objectMapper, response, ErrorCode.FORBIDDEN)))
        .addFilterAfter(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /** 미인증(401)·권한 부족(403) 응답을 공통 ErrorResponse 포맷으로 직렬화한다(backend/CLAUDE.md §4). */
  private static void writeError(
      ObjectMapper objectMapper, HttpServletResponse response, ErrorCode errorCode)
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
