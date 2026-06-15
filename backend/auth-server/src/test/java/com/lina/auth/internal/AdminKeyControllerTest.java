package com.lina.auth.internal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.auth.config.InternalApiKeyFilter;
import com.lina.auth.config.JwtAuthenticationFilter;
import com.lina.auth.config.SecurityConfig;
import com.lina.auth.internal.dto.AdminKeyActivateResponse;
import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Feature 6 — Admin Key 내부 API 계약 + `/internal/admin/**` 호출자 제한 검증. Feature 5 와 동일하게 내부 키 없는 외부
 * 호출·사용자 JWT 를 차단(키/credential 미반환)하는 케이스를 반드시 포함한다.
 */
@WebMvcTest(
    value = AdminKeyController.class,
    properties = "lina.internal.api-key=test-internal-key")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class AdminKeyControllerTest {

  private static final String ACTIVATE = "/internal/admin/key/activate";
  private static final String DEACTIVATE = "/internal/admin/key/deactivate";
  private static final String API_KEY_HEADER = "X-Internal-Api-Key";
  private static final String API_KEY = "test-internal-key";
  private static final String ADMIN_USER_ID = "712020:admin";
  private static final String BODY = "{\"adminUserId\":\"712020:admin\",\"jobId\":\"job-1\"}";

  @Autowired private MockMvc mockMvc;

  @MockBean private AdminKeyService adminKeyService;

  @MockBean private JwtProvider jwtProvider;

  // --- 호출 주체 제한 ---

  @Test
  @DisplayName("activate: 내부 키 없는 외부 호출은 401 로 차단")
  void shouldBlockActivateWithoutInternalKey() throws Exception {
    mockMvc
        .perform(post(ACTIVATE).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("deactivate: 내부 키 없는 외부 호출은 401 로 차단")
  void shouldBlockDeactivateWithoutInternalKey() throws Exception {
    mockMvc
        .perform(post(DEACTIVATE).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("위조된 내부 키는 401 로 차단")
  void shouldBlockForgedInternalKey() throws Exception {
    mockMvc
        .perform(
            post(ACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header(API_KEY_HEADER, "forged-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("사용자 Bearer JWT 는 내부 키가 아니므로 403 으로 차단")
  void shouldBlockUserBearerJwt() throws Exception {
    given(jwtProvider.verifyAccessToken("user-access"))
        .willReturn(new JwtClaims("712020:user", List.of("g-1"), "ADMIN"));

    mockMvc
        .perform(
            post(ACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header("Authorization", "Bearer user-access"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  // --- 성공 ---

  @Test
  @DisplayName("activate: 유효한 내부 키 호출은 200 + expirationTime")
  void shouldActivate() throws Exception {
    given(adminKeyService.activate(ADMIN_USER_ID, "job-1"))
        .willReturn(new AdminKeyActivateResponse("2026-06-15T12:00:00.000Z"));

    mockMvc
        .perform(
            post(ACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expirationTime").value("2026-06-15T12:00:00.000Z"));
  }

  @Test
  @DisplayName("deactivate: 유효한 내부 키 호출은 200")
  void shouldDeactivate() throws Exception {
    mockMvc
        .perform(
            post(DEACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isOk());

    verify(adminKeyService).deactivate(ADMIN_USER_ID, "job-1");
  }

  // --- 에러 정책 ---

  @Test
  @DisplayName("adminUserId 누락 시 400")
  void shouldReturn400WhenAdminUserIdMissing() throws Exception {
    mockMvc
        .perform(
            post(ACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"job-1\"}")
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("jobId 누락 시 400")
  void shouldReturn400WhenJobIdMissing() throws Exception {
    mockMvc
        .perform(
            post(DEACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"adminUserId\":\"712020:admin\"}")
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("role != ADMIN 은 403")
  void shouldMapNonAdminTo403() throws Exception {
    given(adminKeyService.activate(anyString(), anyString()))
        .willThrow(new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다"));

    mockMvc
        .perform(
            post(ACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("credential 없음은 404")
  void shouldMapMissingCredentialTo404() throws Exception {
    given(adminKeyService.activate(anyString(), anyString()))
        .willThrow(
            new BizException(
                ErrorCode.RESOURCE_NOT_FOUND, "저장된 admin Atlassian credential 이 없습니다."));

    mockMvc
        .perform(
            post(ACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("Atlassian 장애는 502 EXTERNAL_SERVICE_ERROR")
  void shouldMapAtlassianFailureTo502() throws Exception {
    willThrow(new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Admin Key 활성화에 실패했습니다."))
        .given(adminKeyService)
        .deactivate(anyString(), anyString());

    mockMvc
        .perform(
            post(DEACTIVATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("EXTERNAL_SERVICE_ERROR"));
  }
}
