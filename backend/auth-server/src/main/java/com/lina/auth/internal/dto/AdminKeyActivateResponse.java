package com.lina.auth.internal.dto;

/**
 * Admin Key activate 응답(wrapper 미적용 raw JSON). BFF 는 응답 body 를 무시(`toBodilessEntity`)하므로 계약 비의존이나,
 * 수동/디버깅 호출 시 만료 시각 확인용으로 Atlassian 의 `expirationTime` 을 그대로 전달한다.
 *
 * @param expirationTime Atlassian admin-key 만료 시각(Atlassian 응답 원문 — ISO-8601 UTC)
 */
public record AdminKeyActivateResponse(String expirationTime) {}
