package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AUTH-04 accessible-resources 응답 원소. {@code id}=cloudId(user_tokens 저장), {@code url}=사이트 base
 * URL(멀티 사이트 선택 대조용). 반환 순서에 의미 없음 — "최근 인가" 추론 금지(current-plans §외부 OAuth 계약 참조).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccessibleResource(String id, String name, String url) {}
