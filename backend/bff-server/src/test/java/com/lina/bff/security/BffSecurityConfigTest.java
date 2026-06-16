package com.lina.bff.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.common.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = BffSecurityConfigTest.TestController.class)
@Import({
  BffSecurityConfig.class,
  JwtCurrentUserProvider.class,
  AdminAuthorizationService.class,
  GlobalExceptionHandler.class,
  BffSecurityConfigTest.TestController.class
})
@EnableWebSecurity
class BffSecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean
  private BffJwtVerifier jwtVerifier;

  @Test
  @DisplayName("/api/auth/** 는 Bearer 없이도 공개한다")
  void shouldPermitAuthGatewayPaths() throws Exception {
    mockMvc
        .perform(get("/api/auth/login"))
        .andExpect(status().isOk())
        .andExpect(content().string("auth-ok"));
  }

  @Test
  @DisplayName("보호 API 는 Bearer 없으면 401 ErrorResponse 를 반환한다")
  void shouldRejectProtectedApiWithoutBearer() throws Exception {
    mockMvc
        .perform(get("/api/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("유효한 Bearer 는 보호 API 를 통과시킨다")
  void shouldAllowProtectedApiWithValidBearer() throws Exception {
    given(jwtVerifier.verifyAccessToken("valid-token"))
        .willReturn(new BffJwtClaims("712020:abc", List.of("group-id-1"), "USER"));

    mockMvc
        .perform(get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(content().string("protected-ok"));
  }

  @Test
  @DisplayName("groups 빈 배열이어도 userId 가 있으면 보호 API 를 통과시킨다")
  void shouldAllowProtectedApiWithEmptyGroups() throws Exception {
    given(jwtVerifier.verifyAccessToken("empty-groups-token"))
        .willReturn(new BffJwtClaims("712020:abc", List.of(), "USER"));

    mockMvc
        .perform(
            get("/api/protected").header(HttpHeaders.AUTHORIZATION, "Bearer empty-groups-token"))
        .andExpect(status().isOk())
        .andExpect(content().string("protected-ok"));
  }

  @Test
  @DisplayName("일반 사용자 JWT 로 관리자 API 를 호출하면 403 을 반환한다")
  void shouldRejectAdminApiForUserRole() throws Exception {
    given(jwtVerifier.verifyAccessToken("user-token"))
        .willReturn(new BffJwtClaims("712020:user", List.of("group-id-1"), "USER"));

    mockMvc
        .perform(
            get("/api/admin/security-test").header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("관리자 JWT 로 관리자 API 를 호출하면 200 을 반환한다")
  void shouldAllowAdminApiForAdminRole() throws Exception {
    given(jwtVerifier.verifyAccessToken("admin-token"))
        .willReturn(new BffJwtClaims("712020:admin", List.of("group-id-1"), "ADMIN"));

    mockMvc
        .perform(
            get("/api/admin/security-test").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(content().string("admin-ok"));
  }

  @RestController
  static class TestController {

    private final AdminAuthorizationService adminAuthorizationService;

    TestController(AdminAuthorizationService adminAuthorizationService) {
      this.adminAuthorizationService = adminAuthorizationService;
    }

    @GetMapping("/api/auth/login")
    String auth() {
      return "auth-ok";
    }

    @GetMapping("/api/protected")
    String protectedApi() {
      return "protected-ok";
    }

    @GetMapping("/api/admin/security-test")
    String adminApi() {
      adminAuthorizationService.requireAdmin();
      return "admin-ok";
    }
  }
}
