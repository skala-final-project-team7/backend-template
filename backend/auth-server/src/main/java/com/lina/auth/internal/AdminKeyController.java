package com.lina.auth.internal;

import com.lina.auth.internal.dto.AdminKeyActivateResponse;
import com.lina.auth.internal.dto.AdminKeyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Admin Key 내부 API endpoint(Feature 6, api-spec §1-4/ADR 0001 §2.1). BFF 의 admin ingest
 *           플로우 전용 — activate(수집 시작 직전)·deactivate(completion event 수신 후). 응답은 내부 계약이라
 *           공통 Wrapper 미적용(raw JSON). 호출 주체는 SecurityConfig 의 X-Internal-Api-Key 인증
 *           (ROLE_INTERNAL)으로 제한된다 — FE/사용자 JWT 차단. body 필수 필드 누락은 @Valid 로 400 처리.
 * 작성일 : 2026-06-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-15, 최초 작성, 3단계 Feature 6 — Admin Key 내부 API
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class AdminKeyController {

  private final AdminKeyService adminKeyService;

  @PostMapping("/internal/admin/key/activate")
  public AdminKeyActivateResponse activate(@RequestBody @Valid AdminKeyRequest request) {
    return adminKeyService.activate(request.adminUserId(), request.jobId());
  }

  @PostMapping("/internal/admin/key/deactivate")
  public void deactivate(@RequestBody @Valid AdminKeyRequest request) {
    adminKeyService.deactivate(request.adminUserId(), request.jobId());
  }
}
