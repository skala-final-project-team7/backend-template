package com.lina.auth.oauth;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.auth.config.JwtAuthenticationFilter;
import com.lina.auth.config.SecurityConfig;
import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProvider;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.token.SessionService;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OAuthLoginService loginService;

  @MockBean private SessionService sessionService;

  @MockBean private JwtProvider jwtProvider;

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
            new OAuthLoginService.CallbackOutcome(
                new LoginTokenResponse("lina-access", "lina-refresh", "2026-06-12T19:00:00+09:00"),
                "/chat"));

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

  // --- Feature 4: refresh ---

  @Test
  @DisplayName("POST /api/auth/refresh 성공 시 공통 Wrapper 로 회전된 토큰을 반환한다")
  void shouldReturnRotatedTokensOnRefresh() throws Exception {
    given(sessionService.refresh("refresh-1"))
        .willReturn(
            new LoginTokenResponse("new-access", "new-refresh", "2026-06-12T19:00:00+09:00"));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"refresh-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("세션 갱신 성공"))
        .andExpect(jsonPath("$.data.accessToken").value("new-access"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
        .andExpect(jsonPath("$.data.expiresAt").value("2026-06-12T19:00:00+09:00"));
  }

  @Test
  @DisplayName("POST /api/auth/refresh 는 refreshToken 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenRefreshTokenMissing() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("POST /api/auth/refresh 는 만료·무효 refresh 를 401 로 매핑한다")
  void shouldMapInvalidRefreshTo401() throws Exception {
    given(sessionService.refresh(anyString()))
        .willThrow(new BizException(ErrorCode.UNAUTHORIZED, "유효하지 않은 refresh token 입니다."));

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"reused-or-expired\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  // --- Feature 4: logout ---

  @Test
  @DisplayName("POST /api/auth/logout 은 유효한 Bearer 로 식별해 refresh 를 무효화하고 data: null 을 반환한다")
  void shouldLogoutWithValidBearer() throws Exception {
    given(jwtProvider.verifyAccessToken("valid-access"))
        .willReturn(new JwtClaims("712020:abc", List.of("g-1"), "USER"));

    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer valid-access"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("로그아웃 성공"))
        .andExpect(jsonPath("$.data").value(nullValue()));

    verify(sessionService).logout("712020:abc");
  }

  @Test
  @DisplayName("POST /api/auth/logout 은 Bearer 누락 시 401 UNAUTHORIZED")
  void shouldRejectLogoutWithoutBearer() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("POST /api/auth/logout 은 서명 위조·만료 Bearer 를 401 로 거부한다")
  void shouldRejectLogoutWithInvalidBearer() throws Exception {
    given(jwtProvider.verifyAccessToken("forged-access")).willThrow(new JwtException("forged"));

    mockMvc
        .perform(post("/api/auth/logout").header("Authorization", "Bearer forged-access"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  // --- Feature 4: /internal/** 차단 ---

  @Test
  @DisplayName("/internal/** 는 미인증 외부 호출을 401 로 차단한다")
  void shouldBlockInternalPathsFromOutside() throws Exception {
    mockMvc
        .perform(get("/internal/auth/admin-confluence-credential"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }
}
