package com.lina.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : auth-server 보안 설정. Feature 3 범위 = OAuth 진입점(/api/auth/login·callback) permitAll +
 *           나머지 전부 차단(default deny). 세션 미사용(stateless Bearer 계약 — api-spec §4-1).
 *           logout Bearer 검증·/internal/** 호출자 제한은 Feature 4/7 에서 확장한다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
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
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/callback")
                    .permitAll()
                    // k8s liveness/readiness probe 용 (application.yml probes.enabled)
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated());
    return http.build();
  }
}
