package com.lina.auth.internal;

import com.lina.auth.internal.dto.ConfluencePagePreviewResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Confluence 페이지 미리보기 내부 프록시 endpoint(Feature P2, api-spec §4-3). 응답은 내부 계약이라
 *           공통 Wrapper 미적용(raw JSON) — BFF 공개 endpoint(P1)가 ApiResponse 로 wrapping 한다.
 *           호출 주체는 SecurityConfig 의 X-Internal-Api-Key 인증(ROLE_INTERNAL)으로 제한된다 — FE/BFF
 *           사용자 JWT/외부 차단. pageId/userId 누락/blank 는 Bean Validation 으로 400 처리한다.
 * 작성일 : 2026-06-18
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-18, 최초 작성, 4단계 Feature P2 — Confluence 미리보기 내부 프록시
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestController
@Validated
@RequiredArgsConstructor
public class InternalConfluencePreviewController {

  private final InternalConfluencePreviewService previewService;

  @GetMapping("/internal/auth/confluence/pages/preview")
  public ConfluencePagePreviewResponse getPagePreview(
      @RequestParam(value = "pageId", required = false) @NotBlank String pageId,
      @RequestParam(value = "userId", required = false) @NotBlank String userId) {
    return previewService.getPagePreview(userId, pageId);
  }
}
