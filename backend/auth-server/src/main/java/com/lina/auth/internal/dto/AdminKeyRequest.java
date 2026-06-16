package com.lina.auth.internal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Admin Key activate/deactivate 공통 요청 body. BFF
 * `AdminKeyActivateRequest`/`AdminKeyDeactivateRequest` 와 동일 필드(`{adminUserId, jobId}`). 누락/blank 는
 * `@Valid` 로 `400` 처리된다.
 *
 * @param adminUserId admin 의 Confluence accountId. role 검증·credential 조회 키
 * @param jobId ingest job 식별자. deactivate idempotency 기준
 */
public record AdminKeyRequest(@NotBlank String adminUserId, @NotBlank String jobId) {}
