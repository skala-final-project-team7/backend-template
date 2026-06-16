package com.lina.auth.internal;

import com.lina.auth.internal.dto.AdminConfluenceCredentialResponse;
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
 * 작성목적 : Data Ingestion Worker 전용 내부 credential 조회 endpoint(Feature 5, api-spec §2-5).
 *           응답은 내부 계약이라 공통 Wrapper 미적용(raw JSON). 호출 주체는 SecurityConfig 의
 *           X-Internal-Api-Key 인증(ROLE_INTERNAL)으로 제한된다 — FE/BFF/외부 차단.
 *           adminUserId 누락/blank 는 Bean Validation 으로 400 처리한다(required=false + @NotBlank).
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 5 — 내부 credential 조회 API
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
public class InternalCredentialController {

  private final InternalCredentialService credentialService;

  @GetMapping("/internal/auth/admin-confluence-credential")
  public AdminConfluenceCredentialResponse getAdminConfluenceCredential(
      @RequestParam(value = "adminUserId", required = false) @NotBlank String adminUserId) {
    return credentialService.getAdminCredential(adminUserId);
  }
}
