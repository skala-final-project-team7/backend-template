package com.lina.auth.oauth;

import com.lina.auth.oauth.OAuthLoginService.CallbackOutcome;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.oauth.dto.RefreshTokenRequest;
import com.lina.auth.token.SessionService;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

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
@Validated
public class AuthController {

  // FE-redirect 모드에서 권한 외 인증 실패(state 불일치·토큰 교환 실패 등)에 싣는 errorCode 값.
  private static final String ERROR_AUTH_FAILED = "AUTH_FAILED";

  private final OAuthLoginService loginService;
  private final SessionService sessionService;

  // 설정 시 callback 이 JSON 대신 이 FE 라우트로 302(SPA 핸드오프). 미설정(기본)이면 JSON 반환 — 기존 계약 유지.
  private final String frontendCallbackUrl;

  public AuthController(
      OAuthLoginService loginService,
      SessionService sessionService,
      @Value("${lina.frontend.callback-url:}") String frontendCallbackUrl) {
    this.loginService = loginService;
    this.sessionService = sessionService;
    this.frontendCallbackUrl = frontendCallbackUrl;
  }

  /** Atlassian authorize 화면으로 302 리다이렉트한다(AUTH-01). mode/returnTo 는 state 에 직렬화·보관. */
  @GetMapping("/login")
  public ResponseEntity<Void> login(
      @RequestParam(value = "returnTo", required = false) String returnTo,
      @RequestParam(value = "mode", required = false) String mode) {
    URI location = loginService.buildAuthorizationRedirectUri(mode, returnTo);
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }

  /**
   * code/state 로 세션을 교환한다. 실패 매핑: state 불일치 400 / Confluence 오류 401 / admin 게이트 403.
   *
   * <p>lina.frontend.callback-url 미설정 시 LINA 세션 토큰을 JSON 으로 반환한다(기존 계약, api-spec §4-1). 설정 시 SPA
   * 핸드오프를 위해 LINA 세션 accessToken 을 쿼리로 실어 FE 콜백 라우트로 302 한다. 노출 대상은 LINA 세션 JWT 뿐이며 Confluence
   * OAuth 토큰은 서버에 보관한다(auth-server CLAUDE §3.1).
   */
  @GetMapping("/callback")
  public ResponseEntity<?> callback(
      @RequestParam @NotBlank String code, @RequestParam @NotBlank String state) {
    // 기본(미설정): LINA 세션 토큰을 JSON 으로 반환한다(기존 계약, api-spec §4-1).
    // 실패는 GlobalExceptionHandler 가 JSON 으로 처리한다.
    if (frontendCallbackUrl == null || frontendCallbackUrl.isBlank()) {
      return ResponseEntity.ok(
          ApiResponse.success(loginService.handleCallback(code, state).tokens(), "로그인 성공"));
    }
    // SPA 핸드오프: 성공/실패를 모두 FE 콜백으로 302 한다(브라우저에 JSON 노출 금지).
    try {
      CallbackOutcome outcome = loginService.handleCallback(code, state);
      return redirectToFrontend(
          builder ->
              builder
                  .queryParam("accessToken", outcome.tokens().accessToken())
                  .queryParam("returnTo", outcome.returnTo()));
    } catch (BizException exception) {
      // 관리자 게이트(403)는 FORBIDDEN, 그 외(state 불일치·토큰 교환 실패 등)는 AUTH_FAILED 로 전달.
      String errorCode =
          exception.getErrorCode() == ErrorCode.FORBIDDEN
              ? ErrorCode.FORBIDDEN.getCode()
              : ERROR_AUTH_FAILED;
      return redirectToFrontend(builder -> builder.queryParam("errorCode", errorCode));
    } catch (RuntimeException exception) {
      return redirectToFrontend(builder -> builder.queryParam("errorCode", ERROR_AUTH_FAILED));
    }
  }

  /** frontendCallbackUrl 에 쿼리를 더해 302 Location 을 만든다(accessToken/returnTo 또는 errorCode). */
  private ResponseEntity<Void> redirectToFrontend(Consumer<UriComponentsBuilder> queryCustomizer) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendCallbackUrl);
    queryCustomizer.accept(builder);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(builder.build().encode().toUri())
        .build();
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
