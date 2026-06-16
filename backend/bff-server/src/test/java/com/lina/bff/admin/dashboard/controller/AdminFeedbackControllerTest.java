package com.lina.bff.admin.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackResponse;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackTrendItemResponse;
import com.lina.bff.admin.dashboard.dto.NegativeFeedbackResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminFeedbackDashboardService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackControllerTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AdminFeedbackDashboardService adminFeedbackDashboardService;

  @BeforeEach
  void setUp() {
    AdminFeedbackController controller =
        new AdminFeedbackController(
            new AdminAuthorizationService(currentUserProvider),
            new AdminDashboardQueryParser(
                Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneId.of("Asia/Seoul"))),
            adminFeedbackDashboardService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /api/admin/feedback 는 ADMIN 요청에 피드백 현황을 반환하고 query parameter 를 적용한다")
  void shouldReturnFeedbackForAdmin() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminFeedbackDashboardService.getFeedback(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new AdminFeedbackResponse(
                3L,
                2L,
                1L,
                0.6667,
                List.of(new AdminFeedbackTrendItemResponse("2026-06-10", 2L, 1L)),
                List.of(
                    new NegativeFeedbackResponse(
                        "fb-1",
                        "a-1",
                        "출처가 이상합니다.",
                        "S3 권한 오류 원인이 뭐야?",
                        "IAM 정책을 확인하세요.",
                        ZonedDateTime.parse("2026-06-10T12:00:00+09:00"))),
                1,
                10));

    mockMvc
        .perform(
            get("/api/admin/feedback")
                .param("period", "daily")
                .param("from", "2026-06-10T00:00:00+09:00")
                .param("to", "2026-06-11T00:00:00+09:00")
                .param("page", "1")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("피드백 현황 조회 성공"))
        .andExpect(jsonPath("$.data.totalCount").value(3))
        .andExpect(jsonPath("$.data.likeCount").value(2))
        .andExpect(jsonPath("$.data.dislikeCount").value(1))
        .andExpect(jsonPath("$.data.positiveRatio").value(0.6667))
        .andExpect(jsonPath("$.data.trend[0].date").value("2026-06-10"))
        .andExpect(jsonPath("$.data.negativeFeedbacks[0].feedbackId").value("fb-1"))
        .andExpect(jsonPath("$.data.negativeFeedbacks[0].question").value("S3 권한 오류 원인이 뭐야?"))
        .andExpect(jsonPath("$.data.negativeFeedbacks[0].answer").value("IAM 정책을 확인하세요."))
        .andExpect(
            jsonPath("$.data.negativeFeedbacks[0].createdAt").value("2026-06-10T12:00:00+09:00"))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(10));

    ArgumentCaptor<AdminDashboardQuery> queryCaptor =
        ArgumentCaptor.forClass(AdminDashboardQuery.class);
    verify(adminFeedbackDashboardService).getFeedback(queryCaptor.capture());
    assertThat(queryCaptor.getValue().period().wireValue()).isEqualTo("daily");
    assertThat(queryCaptor.getValue().timeRange().fromUtc())
        .isEqualTo(Instant.parse("2026-06-09T15:00:00Z"));
    assertThat(queryCaptor.getValue().timeRange().toUtc())
        .isEqualTo(Instant.parse("2026-06-10T15:00:00Z"));
    assertThat(queryCaptor.getValue().pageRequest().page()).isEqualTo(1);
    assertThat(queryCaptor.getValue().pageRequest().size()).isEqualTo(10);
  }

  @Test
  @DisplayName("GET /api/admin/feedback 는 미인증 요청을 401 로 차단한다")
  void shouldRejectUnauthenticatedRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("");

    mockMvc
        .perform(get("/api/admin/feedback"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    verify(adminFeedbackDashboardService, never()).getFeedback(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("GET /api/admin/feedback 는 일반 사용자 요청을 403 으로 차단한다")
  void shouldRejectUserRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    mockMvc
        .perform(get("/api/admin/feedback"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    verify(adminFeedbackDashboardService, never()).getFeedback(org.mockito.ArgumentMatchers.any());
  }
}
