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
import com.lina.auth.internal.dto.ConfluencePagePreviewResponse;
import com.lina.auth.jwt.JwtProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Feature P2 — Confluence 미리보기 내부 endpoint 계약(§4-3) + /internal 호출자 제한 검증. 미인증·비내부 호출 차단(본문 비노출)과
 * 400/404/502 매핑을 고정한다.
 */
@WebMvcTest(
    value = InternalConfluencePreviewController.class,
    properties = "lina.internal.api-key=test-internal-key")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, InternalApiKeyFilter.class})
class InternalConfluencePreviewControllerTest {

  private static final String ENDPOINT = "/internal/auth/confluence/pages/preview";
  private static final String API_KEY_HEADER = "X-Internal-Api-Key";
  private static final String API_KEY = "test-internal-key";
  private static final String USER_ID = "712020:abc";
  private static final String PAGE_ID = "12345";

  @Autowired private MockMvc mockMvc;

  @MockBean private InternalConfluencePreviewService previewService;

  @MockBean private JwtProvider jwtProvider;

  // --- 호출 주체 제한 (내부 service auth — FE/BFF 사용자 JWT/외부 차단) ---

  @Test
  @DisplayName("내부 키 없는 외부 호출은 401 로 차단하고 본문을 반환하지 않는다")
  void shouldBlockExternalCallWithoutInternalKey() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("pageId", PAGE_ID).param("userId", USER_ID))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.bodyViewValue").doesNotExist());
  }

  @Test
  @DisplayName("위조된 내부 키는 401 로 차단한다")
  void shouldBlockForgedInternalKey() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("pageId", PAGE_ID)
                .param("userId", USER_ID)
                .header(API_KEY_HEADER, "forged-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  // --- 성공 계약 (wrapper 미적용 raw JSON) ---

  @Test
  @DisplayName("유효한 내부 키 호출은 미리보기 필드를 wrapper 없이 반환한다")
  void shouldReturnPreviewWithValidInternalKey() throws Exception {
    given(previewService.getPagePreview(USER_ID, PAGE_ID))
        .willReturn(
            new ConfluencePagePreviewResponse(
                PAGE_ID,
                "S3 트러블슈팅 가이드",
                "Cloud Control Center",
                "Platform Team",
                "2026-04-15T18:30:00+09:00",
                List.of("Cloud Control Center", "AWS", "S3", "S3 트러블슈팅 가이드"),
                "https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3",
                "<h1>S3</h1><p>권한 오류는...</p>"));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("pageId", PAGE_ID)
                .param("userId", USER_ID)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pageId").value(PAGE_ID))
        .andExpect(jsonPath("$.title").value("S3 트러블슈팅 가이드"))
        .andExpect(jsonPath("$.spaceName").value("Cloud Control Center"))
        .andExpect(jsonPath("$.authorName").value("Platform Team"))
        .andExpect(jsonPath("$.updatedAt").value("2026-04-15T18:30:00+09:00"))
        .andExpect(jsonPath("$.breadcrumbs[0]").value("Cloud Control Center"))
        .andExpect(jsonPath("$.breadcrumbs[3]").value("S3 트러블슈팅 가이드"))
        .andExpect(
            jsonPath("$.pageUrl")
                .value("https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3"))
        .andExpect(jsonPath("$.bodyViewValue").value("<h1>S3</h1><p>권한 오류는...</p>"))
        // 내부 계약은 wrapper 미적용 + 토큰 미노출
        .andExpect(jsonPath("$.isSuccess").doesNotExist())
        .andExpect(content().string(Matchers.not(Matchers.containsString("accessToken"))));
  }

  // --- 에러 정책 ---

  @Test
  @DisplayName("pageId 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenPageIdMissing() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("userId", USER_ID).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("userId 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenUserIdMissing() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("pageId", PAGE_ID).header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("사용자/토큰/페이지 없음은 404 RESOURCE_NOT_FOUND 로 매핑된다")
  void shouldMapNotFoundTo404() throws Exception {
    given(previewService.getPagePreview(anyString(), anyString()))
        .willThrow(
            new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Confluence 페이지 미리보기를 찾을 수 없습니다"));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("pageId", PAGE_ID)
                .param("userId", USER_ID)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("Confluence 일시 장애는 502 EXTERNAL_SERVICE_ERROR 로 매핑된다")
  void shouldMapTransientFailureTo502() throws Exception {
    given(previewService.getPagePreview(anyString(), anyString()))
        .willThrow(
            new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Confluence 페이지 조회에 실패했습니다."));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("pageId", PAGE_ID)
                .param("userId", USER_ID)
                .header(API_KEY_HEADER, API_KEY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("EXTERNAL_SERVICE_ERROR"));
  }
}
