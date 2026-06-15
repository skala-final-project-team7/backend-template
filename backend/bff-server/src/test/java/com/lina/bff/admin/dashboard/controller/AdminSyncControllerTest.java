package com.lina.bff.admin.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminSyncResponse;
import com.lina.bff.admin.dashboard.dto.SyncHistoryItemResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminSyncService;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminSyncControllerTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AdminSyncService adminSyncService;

  @BeforeEach
  void setUp() {
    AdminSyncController controller =
        new AdminSyncController(
            new AdminAuthorizationService(currentUserProvider),
            new AdminDashboardQueryParser(
                Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneId.of("Asia/Seoul"))),
            adminSyncService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
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
  @DisplayName("GET /api/admin/sync 는 ADMIN 요청에 동기화 이력을 반환하고 기간/페이지를 적용한다")
  void shouldReturnSyncHistoryForAdmin() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminSyncService.getSyncHistory(any()))
        .thenReturn(
            new AdminSyncResponse(
                List.of(
                    new SyncHistoryItemResponse(
                        "sync-001",
                        "COMPLETED",
                        12,
                        1,
                        45,
                        ZonedDateTime.parse("2026-06-10T17:00:00+09:00")))));

    mockMvc
        .perform(
            get("/api/admin/sync")
                .param("from", "2026-06-10T00:00:00+09:00")
                .param("to", "2026-06-11T00:00:00+09:00")
                .param("page", "2")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("동기화 이력 조회 성공"))
        .andExpect(jsonPath("$.data.syncHistory[0].syncId").value("sync-001"))
        .andExpect(jsonPath("$.data.syncHistory[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.syncHistory[0].updatedPages").value(12))
        .andExpect(jsonPath("$.data.syncHistory[0].deletedPages").value(1))
        .andExpect(jsonPath("$.data.syncHistory[0].duration").value(45))
        .andExpect(
            jsonPath("$.data.syncHistory[0].completedAt").value("2026-06-10T17:00:00+09:00"));

    ArgumentCaptor<AdminDashboardQuery> queryCaptor =
        ArgumentCaptor.forClass(AdminDashboardQuery.class);
    verify(adminSyncService).getSyncHistory(queryCaptor.capture());
    assertThat(queryCaptor.getValue().timeRange().fromUtc())
        .isEqualTo(Instant.parse("2026-06-09T15:00:00Z"));
    assertThat(queryCaptor.getValue().timeRange().toUtc())
        .isEqualTo(Instant.parse("2026-06-10T15:00:00Z"));
    assertThat(queryCaptor.getValue().pageRequest().page()).isEqualTo(2);
    assertThat(queryCaptor.getValue().pageRequest().size()).isEqualTo(10);
  }

  @Test
  @DisplayName("GET /api/admin/sync 는 미인증 요청을 401 로 차단한다")
  void shouldRejectUnauthenticatedRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("");

    mockMvc
        .perform(get("/api/admin/sync"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    verify(adminSyncService, never()).getSyncHistory(any());
  }

  @Test
  @DisplayName("GET /api/admin/sync 는 일반 사용자 요청을 403 으로 차단한다")
  void shouldRejectUserRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    mockMvc
        .perform(get("/api/admin/sync"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    verify(adminSyncService, never()).getSyncHistory(any());
  }
}
