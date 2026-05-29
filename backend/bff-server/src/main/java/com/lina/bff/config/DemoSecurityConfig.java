package com.lina.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 중간발표(인증 없음) 한정 SecurityFilterChain. 전 경로 permitAll + CSRF 비활성으로
 *           BFF API 가 인증 없이 동작하도록 한다.
 *
 * NOTE: 중간발표 한정, 3단계에서 JWT 검증으로 대체. 본 빈은 3단계 Authorization Server 도입 시
 *       JWT Resource Server 기반 SecurityFilterChain 으로 교체되며 삭제 대상이다.
 *       production Controller/Service 에 인증 비활성화 분기를 추가하지 않고, 본 클래스 + CurrentUserProvider
 *       추상화로 인증 부재 경계를 격리한다 (backend/bff-server/current-plans.md §2단계 Feature 2).
 *
 * 작성일 : 2026-05-21
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-21, 최초 작성, 2단계 Feature 2 — 데모용 permitAll 체인 도입
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring Security 6.3.x
 * --------------------------------------------------
 * </pre>
 */
@Configuration
@EnableWebSecurity
public class DemoSecurityConfig {

  @Bean
  SecurityFilterChain demoSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
