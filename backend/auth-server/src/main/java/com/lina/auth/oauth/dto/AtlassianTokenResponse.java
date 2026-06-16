package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lina.auth.support.SensitiveValues;

/**
 * AUTH-02/03 토큰 엔드포인트 응답(Atlassian 와이어 snake_case). 토큰 원문은 로그 금지 — MySQL 암호화 저장(user_tokens) 외 보관하지
 * 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtlassianTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("scope") String scope) {

  /** 토큰 원문 로그 노출 방지 — access/refresh 는 마스킹, non-secret(expiresIn/scope)만 노출. */
  @Override
  public String toString() {
    return "AtlassianTokenResponse[accessToken="
        + SensitiveValues.mask(accessToken)
        + ", refreshToken="
        + SensitiveValues.mask(refreshToken)
        + ", expiresIn="
        + expiresIn
        + ", scope="
        + scope
        + "]";
  }
}
