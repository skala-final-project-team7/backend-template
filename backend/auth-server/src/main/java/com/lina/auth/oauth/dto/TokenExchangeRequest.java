package com.lina.auth.oauth.dto;

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
 * 작성목적 : Atlassian 토큰 교환 요청 파라미터 DTO
 * --------------------------------------------------
 * </pre>
 */
@Builder
@Getter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TokenExchangeRequest {

  private final String grantType;
  private final String clientId;
  private final String clientSecret;
  private final String code;
  private final String redirectUri;
}
