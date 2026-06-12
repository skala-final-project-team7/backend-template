package com.lina.bff.admin.dashboard.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.GlobalExceptionHandler;
import com.lina.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(MockitoExtension.class)
class AdminDashboardAccessControllerTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    AdminAuthorizationService authorizationService =
        new AdminAuthorizationService(currentUserProvider);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestAdminDashboardController(authorizationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("관리자 대시보드 공통 권한 경계는 ADMIN 요청을 통과시킨다")
  void shouldAllowAdminRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");

    mockMvc
        .perform(get("/api/admin/dashboard-access-test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.data").value("ok"));
  }

  @Test
  @DisplayName("관리자 대시보드 공통 권한 경계는 미인증 요청을 401 로 차단한다")
  void shouldRejectUnauthenticatedRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("");

    mockMvc
        .perform(get("/api/admin/dashboard-access-test"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("관리자 대시보드 공통 권한 경계는 일반 사용자 요청을 403 으로 차단한다")
  void shouldRejectUserRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    mockMvc
        .perform(get("/api/admin/dashboard-access-test"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  @RestController
  @RequestMapping("/api/admin")
  static class TestAdminDashboardController {

    private final AdminAuthorizationService adminAuthorizationService;

    TestAdminDashboardController(AdminAuthorizationService adminAuthorizationService) {
      this.adminAuthorizationService = adminAuthorizationService;
    }

    @GetMapping("/dashboard-access-test")
    ApiResponse<String> accessTest() {
      adminAuthorizationService.requireAdmin();
      return ApiResponse.success("ok");
    }
  }
}
