package com.lina.bff.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** BFF 운영 기본 보안 설정. `/api/auth/**` 외 BFF API 는 LINA access JWT 를 요구한다. */
@Configuration
@EnableWebSecurity
@Profile("!demo")
public class BffSecurityConfig {

  @Bean
  BffJwtAuthenticationFilter bffJwtAuthenticationFilter(BffJwtVerifier jwtVerifier) {
    return new BffJwtAuthenticationFilter(jwtVerifier);
  }

  @Bean
  SecurityFilterChain bffSecurityFilterChain(
      HttpSecurity http,
      BffJwtAuthenticationFilter jwtAuthenticationFilter,
      ObjectMapper objectMapper)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/info",
                        "/actuator/metrics",
                        "/actuator/metrics/**",
                        "/actuator/prometheus")
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
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

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
