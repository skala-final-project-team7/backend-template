package com.lina.auth.oauth.dto;

/**
 * callback 성공 응답 data (docs/api-spec.md §4-1). LINA 세션 토큰만 포함한다 — Confluence OAuth 토큰은 FE 미노출(서버
 * 암호화 보관). {@code expiresAt}=access JWT 만료 시각(KST, ISO-8601 offset).
 */
public record LoginTokenResponse(String accessToken, String refreshToken, String expiresAt) {}
