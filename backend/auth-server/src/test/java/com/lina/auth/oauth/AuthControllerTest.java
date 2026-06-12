package com.lina.auth.oauth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.auth.config.SecurityConfig;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OAuthLoginService loginService;

  @Test
  @DisplayName("GET /api/auth/login 은 Atlassian authorize 로 302 리다이렉트한다(Wrapper 미적용)")
  void shouldRedirectToAtlassianAuthorize() throws Exception {
    given(loginService.buildAuthorizationRedirectUri("admin", "/admin"))
        .willReturn(URI.create("https://auth.atlassian.com/authorize?state=state-123"));

    mockMvc
        .perform(get("/api/auth/login").param("mode", "admin").param("returnTo", "/admin"))
        .andExpect(status().isFound())
        .andExpect(
            header().string("Location", "https://auth.atlassian.com/authorize?state=state-123"));
  }

  @Test
  @DisplayName("GET /api/auth/login 은 mode/returnTo 생략을 허용한다")
  void shouldRedirectWithoutOptionalParams() throws Exception {
    given(loginService.buildAuthorizationRedirectUri(null, null))
        .willReturn(URI.create("https://auth.atlassian.com/authorize?state=state-456"));

    mockMvc
        .perform(get("/api/auth/login"))
        .andExpect(status().isFound())
        .andExpect(
            header().string("Location", "https://auth.atlassian.com/authorize?state=state-456"));
  }

  @Test
  @DisplayName("GET /api/auth/callback 성공 시 공통 Wrapper 로 access/refresh/expiresAt 을 반환한다")
  void shouldReturnLoginTokensOnCallback() throws Exception {
    given(loginService.handleCallback("auth-code-1", "state-123"))
        .willReturn(
            new LoginTokenResponse("lina-access", "lina-refresh", "2026-06-12T19:00:00+09:00"));

    mockMvc
        .perform(get("/api/auth/callback").param("code", "auth-code-1").param("state", "state-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("로그인 성공"))
        .andExpect(jsonPath("$.data.accessToken").value("lina-access"))
        .andExpect(jsonPath("$.data.refreshToken").value("lina-refresh"))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-06-12T19:00:00+09:00"));
  }

  @Test
  @DisplayName("GET /api/auth/callback 은 code 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenCodeMissing() throws Exception {
    mockMvc
        .perform(get("/api/auth/callback").param("state", "state-123"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("GET /api/auth/callback 은 state 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenStateMissing() throws Exception {
    mockMvc
        .perform(get("/api/auth/callback").param("code", "auth-code-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("state 불일치는 400 INVALID_REQUEST 로 매핑된다")
  void shouldMapStateMismatchTo400() throws Exception {
    given(loginService.handleCallback(anyString(), anyString()))
        .willThrow(new BizException(ErrorCode.INVALID_REQUEST, "유효하지 않은 state 입니다."));

    mockMvc
        .perform(get("/api/auth/callback").param("code", "auth-code-1").param("state", "forged"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("code 무효·Confluence 오류는 401 UNAUTHORIZED 로 매핑된다")
  void shouldMapOauthFailureTo401() throws Exception {
    given(loginService.handleCallback(anyString(), anyString()))
        .willThrow(new BizException(ErrorCode.UNAUTHORIZED, "Confluence 인증에 실패했습니다."));

    mockMvc
        .perform(get("/api/auth/callback").param("code", "bad-code").param("state", "state-123"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("mode=admin 게이트 거부는 403 FORBIDDEN + 안내 메시지로 매핑된다")
  void shouldMapAdminGateTo403WithMessage() throws Exception {
    given(loginService.handleCallback(anyString(), anyString()))
        .willThrow(new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다"));

    mockMvc
        .perform(get("/api/auth/callback").param("code", "auth-code-1").param("state", "state-123"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("관리자 권한이 없는 계정입니다"));
  }
}
