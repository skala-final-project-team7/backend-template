package com.lina.auth.internal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.auth.config.InternalApiKeyFilter;
import com.lina.auth.config.JwtAuthenticationFilter;
import com.lina.auth.config.SecurityConfig;
import com.lina.auth.internal.dto.AdminConfluenceCredentialResponse;
import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Feature 5 — 내부 credential 조회 API 계약(docs/api-spec.md §2-5) + /internal 호출자 제한 검증. 이 API 는 실
 * Confluence OAuth 토큰을 반환하므로 미인증·비내부 호출 차단(토큰 미반환) 케이스를 반드시 포함한다.
 */
@WebMvcTest(
    value = InternalCredentialController.class,
    properties = "lina.internal.api-key=test-internal-key")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class InternalCredentialControllerTest {

  private static final String ENDPOINT = "/internal/auth/admin-confluence-credential";
  private static final String API_KEY_HEADER = "X-Internal-Api-Key";
  private static final String API_KEY = "test-internal-key";
  private static final String ADMIN_USER_ID = "712020:admin";

  @Autowired private MockMvc mockMvc;

  @MockitoBean
  private InternalCredentialService credentialService;

  @MockitoBean
  private JwtProvider jwtProvider;

  // --- 호출 주체 제한 (내부 service auth — FE/BFF/외부 차단) ---

  @Test
  @DisplayName("내부 키 없는 외부 호출은 401 로 차단하고 토큰을 반환하지 않는다")
  void shouldBlockExternalCallWithoutInternalKey() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.accessToken").doesNotExist())
        .andExpect(content().string(Matchers.not(Matchers.containsString("conf-access"))));
  }

  @Test
  @DisplayName("위조된 내부 키는 401 로 차단하고 토큰을 반환하지 않는다")
  void shouldBlockForgedInternalKey() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, "forged-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());
  }

  @Test
  @DisplayName("사용자 Bearer JWT(FE/BFF 경로)는 내부 키가 아니므로 403 으로 차단한다")
  void shouldBlockUserBearerJwt() throws Exception {
    given(jwtProvider.verifyAccessToken("user-access"))
        .willReturn(new JwtClaims("712020:user", List.of("g-1"), "ADMIN"));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("adminUserId", ADMIN_USER_ID)
                .header("Authorization", "Bearer user-access"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());
  }

  // --- 성공 계약 (§2-5 — wrapper 미적용 raw JSON, refreshToken 미노출) ---

  @Test
  @DisplayName("유효한 내부 키 호출은 accessToken/cloudId/siteUrl/expiresAt 을 wrapper 없이 반환한다")
  void shouldReturnCredentialWithValidInternalKey() throws Exception {
    given(credentialService.getAdminCredential(ADMIN_USER_ID))
        .willReturn(
            new AdminConfluenceCredentialResponse(
                "conf-access",
                "11111111-2222-3333-4444-555555555555",
                "https://your-site.atlassian.net",
                "2026-06-12T20:00:00+09:00"));

    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("conf-access"))
        .andExpect(jsonPath("$.cloudId").value("11111111-2222-3333-4444-555555555555"))
        .andExpect(jsonPath("$.siteUrl").value("https://your-site.atlassian.net"))
        .andExpect(jsonPath("$.expiresAt").value("2026-06-12T20:00:00+09:00"))
        // 내부 계약은 wrapper 미적용 (§2-5 응답 예시)
        .andExpect(jsonPath("$.isSuccess").doesNotExist())
        // refreshToken 은 어떤 경우에도 응답하지 않는다
        .andExpect(jsonPath("$.refreshToken").doesNotExist());
  }

  // --- 에러 정책 ---

  @Test
  @DisplayName("adminUserId 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenAdminUserIdMissing() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("adminUserId 가 blank 면 400 INVALID_REQUEST")
  void shouldReturn400WhenAdminUserIdBlank() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", " ").header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("role != ADMIN 은 403 FORBIDDEN 으로 매핑된다")
  void shouldMapNonAdminTo403() throws Exception {
    given(credentialService.getAdminCredential(anyString()))
        .willThrow(new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다"));

    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", "712020:user").header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("사용자·credential 없음은 404 RESOURCE_NOT_FOUND 로 매핑된다")
  void shouldMapMissingCredentialTo404() throws Exception {
    given(credentialService.getAdminCredential(anyString()))
        .willThrow(
            new BizException(ErrorCode.RESOURCE_NOT_FOUND, "저장된 Confluence credential 이 없습니다."));

    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("refresh invalid_grant 는 401 UNAUTHORIZED 로 매핑된다(재로그인 필요)")
  void shouldMapInvalidGrantTo401() throws Exception {
    given(credentialService.getAdminCredential(anyString()))
        .willThrow(new BizException(ErrorCode.UNAUTHORIZED, "Confluence 재로그인이 필요합니다."));

    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("Atlassian 일시 장애는 502 EXTERNAL_SERVICE_ERROR 로 매핑된다")
  void shouldMapTransientAtlassianFailureTo502() throws Exception {
    given(credentialService.getAdminCredential(anyString()))
        .willThrow(new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Atlassian 토큰 갱신에 실패했습니다."));

    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("EXTERNAL_SERVICE_ERROR"));
  }
}
