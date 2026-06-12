package com.lina.auth.internal.dto;

/**
 * §2-5 내부 credential 조회 응답(wrapper 미적용 raw JSON). refreshToken·admin API Token 은 필드 자체를 두지 않아 노출을
 * 구조적으로 차단한다. accessToken 평문은 내부 API 한정 — 본문 로그/tracing 금지.
 *
 * @param accessToken admin Confluence OAuth access token (게이트웨이 콘텐츠 조회용 Bearer)
 * @param cloudId 게이트웨이 URL(api.atlassian.com/ex/confluence/{cloudId}/...) 구성용
 * @param siteUrl admin_atlassian_credential.site_url — 출처 URL absolute 정규화용(secret 아님)
 * @param expiresAt access token 만료 시각(KST ISO-8601 offset — api-spec Common 시간 표기)
 */
public record AdminConfluenceCredentialResponse(
    String accessToken, String cloudId, String siteUrl, String expiresAt) {}
