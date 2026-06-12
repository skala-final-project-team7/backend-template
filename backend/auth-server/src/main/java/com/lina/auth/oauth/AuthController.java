package com.lina.auth.oauth;

import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.oauth.dto.RefreshTokenRequest;
import com.lina.auth.token.SessionService;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : FE-facing 인증 엔드포인트(/api/auth/login·callback — docs/api-spec.md §4-1).
 *           BFF gateway 가 /api/auth/** 를 path-through 라우팅하며 처리 책임은 본 Controller 에 있다.
 *           login 은 302 리다이렉트(Wrapper 미적용), callback 은 공통 Wrapper 로 LINA 세션 토큰을 반환한다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 *   - 2026-06-12, 3단계 Feature 4 — 세션 관리, /refresh(Rotating)·/logout(Bearer 식별) 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final OAuthLoginService loginService;
  private final SessionService sessionService;

  public AuthController(OAuthLoginService loginService, SessionService sessionService) {
    this.loginService = loginService;
    this.sessionService = sessionService;
  }

  /** Atlassian authorize 화면으로 302 리다이렉트한다(AUTH-01). mode/returnTo 는 state 에 직렬화·보관. */
  @GetMapping("/login")
  public ResponseEntity<Void> login(
      @RequestParam(value = "returnTo", required = false) String returnTo,
      @RequestParam(value = "mode", required = false) String mode) {
    URI location = loginService.buildAuthorizationRedirectUri(mode, returnTo);
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }

  /** code/state 로 세션을 교환한다. 실패 매핑: state 불일치 400 / Confluence 오류 401 / admin 게이트 403. */
  @GetMapping("/callback")
  public ApiResponse<LoginTokenResponse> callback(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state) {
    if (code == null || code.isBlank()) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "code 는 필수입니다.");
    }
    if (state == null || state.isBlank()) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "state 는 필수입니다.");
    }
    return ApiResponse.success(loginService.handleCallback(code, state), "로그인 성공");
  }

  /** Rotating refresh — 새 access/refresh 발급, 이전 refresh 무효화. 만료·무효 시 401(api-spec §4-1). */
  @PostMapping("/refresh")
  public ApiResponse<LoginTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(sessionService.refresh(request.refreshToken()), "세션 갱신 성공");
  }

  /** Bearer 로 식별해 refresh token 을 무효화한다. 인증은 SecurityConfig(JWT 필터)가 보장한다. */
  @PostMapping("/logout")
  public ApiResponse<Void> logout(Authentication authentication) {
    sessionService.logout(authentication.getName());
    return ApiResponse.success(null, "로그아웃 성공");
  }
}
