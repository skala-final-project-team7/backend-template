package com.lina.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.auth.internal.InternalCredentialController;
import com.lina.auth.internal.InternalCredentialService;
import com.lina.auth.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Feature 7 — INTERNAL_API_KEY 가 미설정(빈 값)이면 /internal/** 내부 인증이 어떤 요청도 통과시키지 않는다(fail-closed).
 *
 * <p>기존 {@code InternalCredentialControllerTest} 는 키가 설정된 상태에서 누락/위조 헤더를 검증한다. 본 테스트는 키 자체가 주입되지 않은
 * 운영 사고 상황(예: Secret 누락 배포)에서 열려버리지 않음을 고정한다. {@code lina.internal.api-key=} 로 빈 키를 주입한다.
 */
@WebMvcTest(value = InternalCredentialController.class, properties = "lina.internal.api-key=")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class InternalApiKeyFailClosedTest {

  private static final String ENDPOINT = "/internal/auth/admin-confluence-credential";
  private static final String API_KEY_HEADER = "X-Internal-Api-Key";
  private static final String ADMIN_USER_ID = "712020:admin";

  @Autowired private MockMvc mockMvc;

  @MockBean private InternalCredentialService credentialService;

  @MockBean private JwtProvider jwtProvider;

  @Test
  @DisplayName("키 미설정 + 헤더 없음 → 401 로 거부한다")
  void deniesWhenKeyUnsetAndNoHeader() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());
  }

  @Test
  @DisplayName("키 미설정이면 어떤 헤더 값을 보내도 통과하지 못한다(빈 키 매칭 금지) → 401")
  void deniesAnyHeaderWhenKeyUnset() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, "any-value"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());

    // 빈 문자열 헤더(예: 미설정 env 가 빈 값으로 전파)도 빈 expectedKey 와 매칭되지 않아야 한다
    mockMvc
        .perform(get(ENDPOINT).param("adminUserId", ADMIN_USER_ID).header(API_KEY_HEADER, ""))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.accessToken").doesNotExist());
  }
}
