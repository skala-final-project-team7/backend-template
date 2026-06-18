package com.lina.bff.confluence.controller;

import com.lina.bff.confluence.dto.ConfluencePagePreviewResponse;
import com.lina.bff.confluence.service.ConfluencePreviewService;
import com.lina.common.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Confluence 페이지 미리보기 공개 endpoint(Feature P1, api-spec §4-3). 출처 hover preview 용으로
 *           pageId 를 받아 auth-server 내부 프록시(P2)를 거쳐 미리보기 DTO 를 ApiResponse 로 반환한다.
 *           인증은 BffSecurityConfig(JWT 필터)가 보장하며(미인증 401), pageId 누락/blank 는 Bean Validation
 *           으로 400 처리한다(required=false + @NotBlank). BFF 는 Confluence 토큰을 보유하지 않는다.
 * 작성일 : 2026-06-18
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-18, 최초 작성, 4단계 Feature P1 — Confluence 미리보기 BFF 공개 endpoint
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestController
@RequestMapping("/api/confluence")
@Validated
@RequiredArgsConstructor
public class ConfluencePreviewController {

  private final ConfluencePreviewService previewService;

  @GetMapping("/pages/preview")
  public ApiResponse<ConfluencePagePreviewResponse> getPagePreview(
      @RequestParam(value = "pageId", required = false) @NotBlank String pageId) {
    return ApiResponse.success(previewService.getPreview(pageId), "Confluence 페이지 미리보기 조회 성공");
  }
}
