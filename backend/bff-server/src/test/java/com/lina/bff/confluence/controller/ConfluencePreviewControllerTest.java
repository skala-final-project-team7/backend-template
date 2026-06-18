package com.lina.bff.confluence.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.confluence.dto.ConfluencePagePreviewResponse;
import com.lina.bff.confluence.service.ConfluencePreviewService;
import com.lina.bff.security.BffJwtClaims;
import com.lina.bff.security.BffJwtVerifier;
import com.lina.bff.security.BffSecurityConfig;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** Feature P1 — 공개 미리보기 endpoint 계약(§4-3) + 인증/검증/에러 매핑 검증. */
@WebMvcTest(controllers = ConfluencePreviewController.class)
@Import(BffSecurityConfig.class)
class ConfluencePreviewControllerTest {

  private static final String ENDPOINT = "/api/confluence/pages/preview";
  private static final String PAGE_ID = "12345";

  @Autowired private MockMvc mockMvc;

  @MockBean private BffJwtVerifier jwtVerifier;
  @MockBean private ConfluencePreviewService previewService;

  private void givenAuthenticated() {
    given(jwtVerifier.verifyAccessToken("valid-token"))
        .willReturn(new BffJwtClaims("712020:abc", List.of("group-id-1"), "USER"));
  }

  @Test
  @DisplayName("Bearer 없으면 401 을 반환하고 service 를 호출하지 않는다")
  void shouldRejectWithoutBearer() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("pageId", PAGE_ID))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

    verifyNoInteractions(previewService);
  }

  @Test
  @DisplayName("정상: 인증 후 미리보기를 ApiResponse 로 반환한다(토큰 미노출)")
  void shouldReturnPreview() throws Exception {
    givenAuthenticated();
    given(previewService.getPreview(PAGE_ID))
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
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.message").value("Confluence 페이지 미리보기 조회 성공"))
        .andExpect(jsonPath("$.data.pageId").value(PAGE_ID))
        .andExpect(jsonPath("$.data.title").value("S3 트러블슈팅 가이드"))
        .andExpect(jsonPath("$.data.spaceName").value("Cloud Control Center"))
        .andExpect(jsonPath("$.data.authorName").value("Platform Team"))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-04-15T18:30:00+09:00"))
        .andExpect(jsonPath("$.data.breadcrumbs[0]").value("Cloud Control Center"))
        .andExpect(
            jsonPath("$.data.pageUrl")
                .value("https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3"))
        .andExpect(jsonPath("$.data.bodyViewValue").value("<h1>S3</h1><p>권한 오류는...</p>"))
        .andExpect(content().string(Matchers.not(Matchers.containsString("accessToken"))));
  }

  @Test
  @DisplayName("pageId 누락 시 400 INVALID_REQUEST")
  void shouldReturn400WhenPageIdMissing() throws Exception {
    givenAuthenticated();

    mockMvc
        .perform(get(ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("내부 RESOURCE_NOT_FOUND 는 404 로 매핑된다")
  void shouldMapNotFoundTo404() throws Exception {
    givenAuthenticated();
    given(previewService.getPreview(anyString()))
        .willThrow(
            new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Confluence 페이지 미리보기를 찾을 수 없습니다"));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("pageId", PAGE_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("내부 EXTERNAL_SERVICE_ERROR 는 502 로 매핑된다")
  void shouldMapExternalErrorTo502() throws Exception {
    givenAuthenticated();
    given(previewService.getPreview(anyString()))
        .willThrow(
            new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Confluence 페이지 미리보기 조회에 실패했습니다."));

    mockMvc
        .perform(
            get(ENDPOINT)
                .param("pageId", PAGE_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("EXTERNAL_SERVICE_ERROR"));
  }
}
