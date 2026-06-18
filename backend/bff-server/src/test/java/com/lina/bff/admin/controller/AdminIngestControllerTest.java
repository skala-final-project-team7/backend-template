package com.lina.bff.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.service.AdminIngestService;
import com.lina.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminIngestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminIngestControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AdminIngestService adminIngestService;

  @Test
  @DisplayName("POST /api/admin/ingest 는 Admin Key activate 실패 시 EXTERNAL_SERVICE_ERROR 를 반환한다")
  void shouldReturnExternalServiceErrorWhenAdminKeyActivationFails() throws Exception {
    when(adminIngestService.startIngest(any()))
        .thenThrow(
            new AuthAdminKeyClient.AuthAdminKeyException(
                "Failed to activate Admin Key", new RuntimeException("auth-server unavailable")));

    mockMvc
        .perform(
            post("/api/admin/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"mode":"full"}
                    """))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(502))
        .andExpect(jsonPath("$.errorCode").value("EXTERNAL_SERVICE_ERROR"))
        .andExpect(jsonPath("$.message").value("Failed to activate Admin Key"));
  }
}
