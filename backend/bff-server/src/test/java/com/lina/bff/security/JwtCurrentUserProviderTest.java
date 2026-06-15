package com.lina.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtCurrentUserProviderTest {

  private final JwtCurrentUserProvider provider = new JwtCurrentUserProvider();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("SecurityContext 의 JWT claim 에서 현재 사용자 정보를 반환한다")
  void shouldReadCurrentUserFromSecurityContextClaims() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new BffJwtClaims("712020:abc", List.of("group-id-1"), "ADMIN"), null));

    assertThat(provider.getUserId()).isEqualTo("712020:abc");
    assertThat(provider.getGroups()).containsExactly("group-id-1");
    assertThat(provider.getRole()).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("인증 정보가 없으면 빈 사용자 정보를 반환한다")
  void shouldReturnEmptyValuesWhenUnauthenticated() {
    assertThat(provider.getUserId()).isEmpty();
    assertThat(provider.getGroups()).isEmpty();
    assertThat(provider.getRole()).isEmpty();
    assertThat(provider.getSpaceKey()).isEmpty();
  }
}
