package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
}
