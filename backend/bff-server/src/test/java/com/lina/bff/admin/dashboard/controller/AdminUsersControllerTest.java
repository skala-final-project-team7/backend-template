package com.lina.bff.admin.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminUserSummaryResponse;
import com.lina.bff.admin.dashboard.dto.AdminUsersResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminUsersService;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminUsersControllerTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AdminUsersService adminUsersService;

  @BeforeEach
  void setUp() {
    AdminUsersController controller =
        new AdminUsersController(
            new AdminAuthorizationService(currentUserProvider),
            new AdminDashboardQueryParser(
                Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC)),
            adminUsersService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /api/admin/users 는 ADMIN 요청에 사용자 현황을 반환한다")
  void shouldReturnUsersForAdmin() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminUsersService.getUsers(any(AdminDashboardQuery.class)))
        .thenReturn(
            new AdminUsersResponse(
                5L,
                2L,
                0,
                20,
                List.of(
                    new AdminUserSummaryResponse(
                        "712020:admin",
                        "관리자",
                        "admin@example.com",
                        "ADMIN",
                        null,
                        null,
                        3L,
                        0L,
                        0L,
                        0L))));

    mockMvc
        .perform(
            get("/api/admin/users")
                .param("page", "0")
                .param("size", "20")
                .param("from", "2026-06-10T00:00:00+09:00")
                .param("to", "2026-06-11T00:00:00+09:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("관리자 사용자 현황 조회 성공"))
        .andExpect(jsonPath("$.data.totalUsers").value(5))
        .andExpect(jsonPath("$.data.dailyActiveUsers").value(2))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.users[0].userId").value("712020:admin"))
        .andExpect(jsonPath("$.data.users[0].conversationCount").value(3));
  }

  @Test
  @DisplayName("GET /api/admin/users 는 미인증 요청을 401 로 차단한다")
  void shouldRejectUnauthenticatedRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("");

    mockMvc
        .perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    verify(adminUsersService, never()).getUsers(any());
  }

  @Test
  @DisplayName("GET /api/admin/users 는 일반 사용자 요청을 403 으로 차단한다")
  void shouldRejectUserRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    mockMvc
        .perform(get("/api/admin/users"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    verify(adminUsersService, never()).getUsers(any());
  }
}
