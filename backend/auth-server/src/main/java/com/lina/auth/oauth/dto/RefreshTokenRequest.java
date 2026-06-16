package com.lina.auth.oauth.dto;

import com.lina.auth.support.SensitiveValues;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/auth/refresh 요청 Body (docs/api-spec.md §4-1). LINA refresh token — Confluence 토큰 아님.
 */
public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken 은 필수입니다.") String refreshToken) {

  /** refresh 토큰 원문 로그 노출 방지. */
  @Override
  public String toString() {
    return "RefreshTokenRequest[refreshToken=" + SensitiveValues.mask(refreshToken) + "]";
  }
}
