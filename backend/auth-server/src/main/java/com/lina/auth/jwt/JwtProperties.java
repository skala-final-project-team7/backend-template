package com.lina.auth.jwt;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : JWT 발급/검증 운영 파라미터 홀더. 서명 키(RS256 PEM)·issuer·TTL 을 전부
 *           `${...}` 환경변수로 주입한다(application.yml `lina.jwt.*`, 평문 secret 금지).
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 2 — JWT 발급/검증 유틸
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
@Getter
public class JwtProperties {

  /** JWT `iss` claim 값. BFF 검증 필터와 동일 값을 공유한다. */
  private final String issuer;

  /** RS256 서명용 개인키(PKCS#8 PEM). auth-server 만 보유한다. */
  private final String privateKey;

  /** RS256 검증용 공개키(X.509 PEM). BFF 는 이 키만 보유해 검증한다. */
  private final String publicKey;

  private final long accessTokenTtlSeconds;
  private final long refreshTokenTtlSeconds;

  public JwtProperties(
      @Value("${lina.jwt.issuer}") String issuer,
      @Value("${lina.jwt.private-key}") String privateKey,
      @Value("${lina.jwt.public-key}") String publicKey,
      @Value("${lina.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
      @Value("${lina.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
    this.issuer = issuer;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
  }
}
