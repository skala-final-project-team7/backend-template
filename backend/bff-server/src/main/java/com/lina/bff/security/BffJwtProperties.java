package com.lina.bff.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** auth-server 와 공유하는 JWT 검증 설정. BFF 는 공개키만 보유한다. */
@Component
public class BffJwtProperties {

  private final String issuer;
  private final String publicKey;

  public BffJwtProperties(
      @Value("${lina.jwt.issuer}") String issuer,
      @Value("${lina.jwt.public-key}") String publicKey) {
    this.issuer = issuer;
    this.publicKey = publicKey;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getPublicKey() {
    return publicKey;
  }
}
