package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.lina.auth.support.SensitiveValues;
import lombok.Builder;
import lombok.Getter;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Atlassian 토큰 교환 요청 파라미터 DTO. grant 별 미사용 필드는 와이어에 싣지 않는다(NON_NULL) —
 *           authorization_code(AUTH-02)는 code/redirect_uri, refresh_token(AUTH-03)은 refresh_token 만 사용.
 * --------------------------------------------------
 * </pre>
 */
@Builder
@Getter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenExchangeRequest {

  private final String grantType;
  private final String clientId;
  private final String clientSecret;
  private final String code;
  private final String redirectUri;
  private final String refreshToken;

  /**
   * 토큰 교환 파라미터 로그 노출 방지 — client-secret/code/refresh 는 마스킹, grantType/clientId/redirectUri(secret
   * 아님)만 노출한다. (@Getter 만 있어 Lombok 은 toString 을 생성하지 않지만, 추후 @ToString 추가 시에도 secret 이 새지 않도록 명시
   * 구현)
   */
  @Override
  public String toString() {
    return "TokenExchangeRequest[grantType="
        + grantType
        + ", clientId="
        + clientId
        + ", clientSecret="
        + SensitiveValues.mask(clientSecret)
        + ", code="
        + SensitiveValues.mask(code)
        + ", redirectUri="
        + redirectUri
        + ", refreshToken="
        + SensitiveValues.mask(refreshToken)
        + "]";
  }
}
