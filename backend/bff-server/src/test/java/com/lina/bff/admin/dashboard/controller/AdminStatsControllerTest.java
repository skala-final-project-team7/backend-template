package com.lina.bff.admin.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse.HourlyAccessTrendItem;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminStatsService;
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
class AdminStatsControllerTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AdminStatsService adminStatsService;

  @BeforeEach
  void setUp() {
    AdminStatsController controller =
        new AdminStatsController(
            new AdminAuthorizationService(currentUserProvider),
            new AdminDashboardQueryParser(
                Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC)),
            adminStatsService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /api/admin/stats 는 ADMIN 요청에 통계 응답을 반환한다")
  void shouldReturnStatsForAdmin() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminStatsService.getStats(any(AdminDashboardQuery.class)))
        .thenReturn(
            new AdminStatsResponse(12L, 3.5, 20L, List.of(new HourlyAccessTrendItem(9, 4L))));

    mockMvc
        .perform(
            get("/api/admin/stats")
                .param("period", "hourly")
                .param("from", "2026-06-10T00:00:00+09:00")
                .param("to", "2026-06-11T00:00:00+09:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("서비스 통계 조회 성공"))
        .andExpect(jsonPath("$.data.dailyQueryCount").value(12))
        .andExpect(jsonPath("$.data.avgResponseTime").value(3.5))
        .andExpect(jsonPath("$.data.totalConversations").value(20))
        .andExpect(jsonPath("$.data.hourlyAccessTrend[0].hour").value(9))
        .andExpect(jsonPath("$.data.hourlyAccessTrend[0].count").value(4));
  }

  @Test
  @DisplayName("GET /api/admin/stats 는 파라미터 생략 시 기본 최근 7일 범위를 사용한다")
  void shouldUseDefaultRangeWhenParamsAreMissing() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminStatsService.getStats(any(AdminDashboardQuery.class)))
        .thenAnswer(
            invocation -> {
              AdminDashboardQuery query = invocation.getArgument(0);
              return new AdminStatsResponse(
                  query.timeRange().fromUtc().toString().equals("2026-06-05T03:00:00Z") ? 1L : 0L,
                  0.0,
                  0L,
                  List.of());
            });

    mockMvc
        .perform(get("/api/admin/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dailyQueryCount").value(1));
  }

  @Test
  @DisplayName("GET /api/admin/stats 는 미인증 요청을 401 로 차단한다")
  void shouldRejectUnauthenticatedRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("");

    mockMvc
        .perform(get("/api/admin/stats"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    verify(adminStatsService, never()).getStats(any());
  }

  @Test
  @DisplayName("GET /api/admin/stats 는 일반 사용자 요청을 403 으로 차단한다")
  void shouldRejectUserRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    mockMvc
        .perform(get("/api/admin/stats"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    verify(adminStatsService, never()).getStats(any());
  }
}
