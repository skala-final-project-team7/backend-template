package com.lina.bff.admin.dashboard.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lina.bff.admin.dashboard.dto.AdminDataResponse;
import com.lina.bff.admin.dashboard.security.AdminAuthorizationService;
import com.lina.bff.admin.dashboard.service.AdminDataService;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.GlobalExceptionHandler;
import java.time.ZonedDateTime;
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
class AdminDataControllerTest {

  private MockMvc mockMvc;

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AdminDataService adminDataService;

  @BeforeEach
  void setUp() {
    AdminDataController controller =
        new AdminDataController(
            new AdminAuthorizationService(currentUserProvider), adminDataService);
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
  @DisplayName("GET /api/admin/data 는 ADMIN 요청에 데이터 현황을 반환한다")
  void shouldReturnDataForAdmin() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");
    when(adminDataService.getData())
        .thenReturn(
            new AdminDataResponse(
                5L,
                1230L,
                187L,
                "2.3 GB",
                8940L,
                ZonedDateTime.parse("2026-05-20T17:00:00+09:00")));

    mockMvc
        .perform(get("/api/admin/data"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("데이터 현황 조회 성공"))
        .andExpect(jsonPath("$.data.totalSpaces").value(5))
        .andExpect(jsonPath("$.data.totalPages").value(1230))
        .andExpect(jsonPath("$.data.totalAttachments").value(187))
        .andExpect(jsonPath("$.data.vectorDbSize").value("2.3 GB"))
        .andExpect(jsonPath("$.data.totalChunks").value(8940))
        .andExpect(jsonPath("$.data.lastSyncAt").value("2026-05-20T17:00:00+09:00"));
  }

  @Test
  @DisplayName("GET /api/admin/data 는 미인증 요청을 401 로 차단한다")
  void shouldRejectUnauthenticatedRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("");

    mockMvc
        .perform(get("/api/admin/data"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    verify(adminDataService, never()).getData();
  }

  @Test
  @DisplayName("GET /api/admin/data 는 일반 사용자 요청을 403 으로 차단한다")
  void shouldRejectUserRequest() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    mockMvc
        .perform(get("/api/admin/data"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    verify(adminDataService, never()).getData();
  }
}
