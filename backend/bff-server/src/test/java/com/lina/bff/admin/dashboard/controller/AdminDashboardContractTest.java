package com.lina.bff.admin.dashboard.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lina.bff.admin.dashboard.dto.AdminDataResponse;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackResponse;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackTrendItemResponse;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse.HourlyAccessTrendItem;
import com.lina.bff.admin.dashboard.dto.AdminSyncResponse;
import com.lina.bff.admin.dashboard.dto.AdminUserSummaryResponse;
import com.lina.bff.admin.dashboard.dto.AdminUsersResponse;
import com.lina.bff.admin.dashboard.dto.NegativeFeedbackResponse;
import com.lina.bff.admin.dashboard.dto.SyncHistoryItemResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminDataService;
import com.lina.bff.admin.dashboard.service.AdminFeedbackDashboardService;
import com.lina.bff.admin.dashboard.service.AdminStatsService;
import com.lina.bff.admin.dashboard.service.AdminSyncService;
import com.lina.bff.admin.dashboard.service.AdminUsersService;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminDashboardContractTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AdminStatsService adminStatsService;
  @Mock private AdminUsersService adminUsersService;
  @Mock private AdminDataService adminDataService;
  @Mock private AdminFeedbackDashboardService adminFeedbackDashboardService;
  @Mock private AdminSyncService adminSyncService;

  @BeforeEach
  void setUp() {
    AdminAuthorizationService authorizationService =
        new AdminAuthorizationService(currentUserProvider);
    AdminDashboardQueryParser queryParser =
        new AdminDashboardQueryParser(
            Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneId.of("Asia/Seoul")));

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AdminStatsController(authorizationService, queryParser, adminStatsService),
                new AdminUsersController(authorizationService, queryParser, adminUsersService),
                new AdminDataController(authorizationService, adminDataService),
                new AdminFeedbackController(
                    authorizationService, queryParser, adminFeedbackDashboardService),
                new AdminSyncController(authorizationService, queryParser, adminSyncService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    JsonMapper.builder()
                        .findAndAddModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
            .build();
  }

  @Test
  @DisplayName("관리자 대시보드 API 5종은 api-spec §4-2 Wrapper, message, field 계약을 만족한다")
  void shouldMatchAdminDashboardApiSpecContract() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminStatsService.getStats(any()))
        .thenReturn(
            new AdminStatsResponse(142L, 3.2, 856L, List.of(new HourlyAccessTrendItem(9, 23L))));
    when(adminUsersService.getUsers(any()))
        .thenReturn(
            new AdminUsersResponse(
                48L,
                12L,
                0,
                20,
                List.of(
                    new AdminUserSummaryResponse(
                        "712020:91b5112c-7b2a-4c3d-8e9f-0a1b2c3d4e5f",
                        "이다연",
                        "dayeon@example.com",
                        "USER",
                        "https://avatar.example/dayeon.png",
                        ZonedDateTime.parse("2026-05-20T18:00:00+09:00"),
                        35L,
                        5L,
                        320L,
                        48L))));
    when(adminDataService.getData())
        .thenReturn(
            new AdminDataResponse(
                5L,
                1230L,
                187L,
                "2.3 GB",
                8940L,
                ZonedDateTime.parse("2026-05-20T17:00:00+09:00")));
    when(adminFeedbackDashboardService.getFeedback(any()))
        .thenReturn(
            new AdminFeedbackResponse(
                320L,
                256L,
                64L,
                0.8,
                List.of(new AdminFeedbackTrendItemResponse("2026-05-20", 52L, 11L)),
                List.of(
                    new NegativeFeedbackResponse(
                        "fb-uuid-101",
                        "msg-uuid-200",
                        "출처가 질문과 관련 없었어요",
                        "S3 권한 오류 원인이 뭐야?",
                        "IAM 정책을 확인하세요...",
                        ZonedDateTime.parse("2026-05-20T18:30:00+09:00"))),
                0,
                20));
    when(adminSyncService.getSyncHistory(any()))
        .thenReturn(
            new AdminSyncResponse(
                List.of(
                    new SyncHistoryItemResponse(
                        "sync-uuid-001",
                        "COMPLETED",
                        12L,
                        1L,
                        45L,
                        ZonedDateTime.parse("2026-05-20T17:00:00+09:00")))));

    mockMvc
        .perform(get("/api/admin/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("서비스 통계 조회 성공"))
        .andExpect(jsonPath("$.data.dailyQueryCount").value(142))
        .andExpect(jsonPath("$.data.avgResponseTime").value(3.2))
        .andExpect(jsonPath("$.data.totalConversations").value(856))
        .andExpect(jsonPath("$.data.hourlyAccessTrend[0].hour").value(9))
        .andExpect(jsonPath("$.data.hourlyAccessTrend[0].count").value(23));

    mockMvc
        .perform(get("/api/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("사용자 현황 조회 성공"))
        .andExpect(jsonPath("$.data.totalUsers").value(48))
        .andExpect(jsonPath("$.data.dailyActiveUsers").value(12))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(
            jsonPath("$.data.users[0].userId").value("712020:91b5112c-7b2a-4c3d-8e9f-0a1b2c3d4e5f"))
        .andExpect(jsonPath("$.data.users[0].email").value("dayeon@example.com"))
        .andExpect(jsonPath("$.data.users[0].role").value("USER"))
        .andExpect(jsonPath("$.data.users[0].conversationCount").value(35))
        .andExpect(jsonPath("$.data.users[0].accessiblePageCount").value(320));

    mockMvc
        .perform(get("/api/admin/data"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("데이터 현황 조회 성공"))
        .andExpect(jsonPath("$.data.totalSpaces").value(5))
        .andExpect(jsonPath("$.data.vectorDbSize").value("2.3 GB"))
        .andExpect(jsonPath("$.data.totalChunks").value(8940))
        .andExpect(jsonPath("$.data.lastSyncAt").value("2026-05-20T17:00:00+09:00"));

    mockMvc
        .perform(get("/api/admin/feedback"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("피드백 현황 조회 성공"))
        .andExpect(jsonPath("$.data.totalCount").value(320))
        .andExpect(jsonPath("$.data.positiveRatio").value(0.8))
        .andExpect(jsonPath("$.data.trend[0].date").value("2026-05-20"))
        .andExpect(jsonPath("$.data.negativeFeedbacks[0].feedbackId").value("fb-uuid-101"))
        .andExpect(jsonPath("$.data.negativeFeedbacks[0].question").value("S3 권한 오류 원인이 뭐야?"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20));

    mockMvc
        .perform(get("/api/admin/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("동기화 이력 조회 성공"))
        .andExpect(jsonPath("$.data.syncHistory[0].syncId").value("sync-uuid-001"))
        .andExpect(jsonPath("$.data.syncHistory[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.syncHistory[0].updatedPages").value(12))
        .andExpect(jsonPath("$.data.syncHistory[0].duration").value(45))
        .andExpect(
            jsonPath("$.data.syncHistory[0].completedAt").value("2026-05-20T17:00:00+09:00"));
  }
}
