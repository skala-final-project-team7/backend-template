package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AUTH-02/03 토큰 엔드포인트 응답(Atlassian 와이어 snake_case). 토큰 원문은 로그 금지 — MySQL 암호화 저장(user_tokens) 외 보관하지
 * 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtlassianTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("scope") String scope) {}
