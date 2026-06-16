package com.lina.bff.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.security.BffJwtClaims;
import com.lina.bff.security.BffJwtVerifier;
import com.lina.bff.security.BffSecurityConfig;
import com.lina.bff.user.dto.UserMeResponse;
import com.lina.bff.user.service.UserMeService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserMeController.class)
@Import(BffSecurityConfig.class)
class UserMeControllerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired private MockMvc mockMvc;

  @MockitoBean
  private BffJwtVerifier jwtVerifier;

  @MockitoBean
  private UserMeService userMeService;

  @Test
  @DisplayName("GET /api/users/me 는 Bearer 인증 후 사용자 정보를 반환한다")
  void shouldReturnCurrentUser() throws Exception {
    given(jwtVerifier.verifyAccessToken("valid-token"))
        .willReturn(new BffJwtClaims("712020:abc", List.of("group-id-1"), "USER"));
    given(userMeService.getCurrentUser())
        .willReturn(
            new UserMeResponse(
                "712020:abc",
                "홍길동",
                "gildong@example.com",
                "USER",
                "https://example.com/avatar.png",
                ZonedDateTime.of(2026, 6, 15, 18, 0, 0, 0, KST)));

    mockMvc
        .perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("사용자 정보 조회 성공"))
        .andExpect(jsonPath("$.data.userId").value("712020:abc"))
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.email").value("gildong@example.com"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/avatar.png"))
        .andExpect(jsonPath("$.data.lastLoginAt").value("2026-06-15T18:00:00+09:00"));
  }

  @Test
  @DisplayName("GET /api/users/me 는 Bearer 없으면 401 을 반환하고 service 를 호출하지 않는다")
  void shouldRejectWithoutBearer() throws Exception {
    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

    verifyNoInteractions(userMeService);
  }
}
