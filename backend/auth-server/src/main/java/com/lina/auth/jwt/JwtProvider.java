package com.lina.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA 세션 토큰 발급/검증 유틸. RS256(개인키 서명/공개키 검증 — BFF 는 공개키만 보유)으로
 *           access JWT(claim: userId/groups/role/iss/iat/exp, camelCase — BFF 검증 필터와 동일 계약)와
 *           refresh token(JWT, 자체 만료 포함)을 발급한다. refresh 는 `tokenType=refresh` claim 으로
 *           access 와 교차 사용을 차단한다(access 는 계약된 claim 셋 그대로 — 추가 claim 없음).
 *           검증은 서명·만료·issuer 를 확인하며 실패 시 io.jsonwebtoken.JwtException 계열을 던진다.
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 2 — JWT 발급/검증 유틸 (backend/rules/auth.md §2)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / jjwt 0.12.x
 * --------------------------------------------------
 * </pre>
 */
@Component
public class JwtProvider {

  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_GROUPS = "groups";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TOKEN_TYPE = "tokenType";
  private static final String TOKEN_TYPE_REFRESH = "refresh";

  private final JwtProperties properties;
  private final PrivateKey privateKey;
  private final PublicKey publicKey;

  public JwtProvider(JwtProperties properties) {
    this.properties = properties;
    this.privateKey = parsePrivateKey(properties.getPrivateKey());
    this.publicKey = parsePublicKey(properties.getPublicKey());
  }

  /** LINA 세션 access JWT 발급. claim 은 호출자가 조회해 전달한 값을 그대로 서명한다. */
  public String issueAccessToken(JwtClaims claims) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(properties.getIssuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(properties.getAccessTokenTtlSeconds())))
        .claim(CLAIM_USER_ID, claims.userId())
        .claim(CLAIM_GROUPS, claims.groups())
        .claim(CLAIM_ROLE, claims.role())
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  /** LINA refresh token 발급. 세션 갱신 식별에 필요한 userId 만 담는다(권한 claim 은 refresh 시 DB 재조회). */
  public String issueRefreshToken(String userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(properties.getIssuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(properties.getRefreshTokenTtlSeconds())))
        .claim(CLAIM_USER_ID, userId)
        .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  /**
   * access JWT 검증(서명·만료·issuer). refresh token 의 교차 사용은 거부한다.
   *
   * @throws JwtException 서명 위조·만료·issuer 불일치·토큰 타입 오용 시
   */
  public JwtClaims verifyAccessToken(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(properties.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    if (claims.get(CLAIM_TOKEN_TYPE) != null) {
      throw new JwtException("access 검증에 사용할 수 없는 토큰 타입입니다.");
    }
    List<?> rawGroups = claims.get(CLAIM_GROUPS, List.class);
    return new JwtClaims(
        claims.get(CLAIM_USER_ID, String.class),
        rawGroups.stream().map(String::valueOf).toList(),
        claims.get(CLAIM_ROLE, String.class));
  }

  /**
   * refresh token 검증(서명·만료·issuer·tokenType) 후 userId 를 반환한다. DB 저장값 대조·회전은 Feature 4
   * SessionService 책임.
   *
   * @throws JwtException 서명 위조·만료·issuer 불일치·토큰 타입 오용 시
   */
  public String verifyRefreshToken(String token) {
    return Jwts.parser()
        .verifyWith(publicKey)
        .requireIssuer(properties.getIssuer())
        .require(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .get(CLAIM_USER_ID, String.class);
  }

  private static PrivateKey parsePrivateKey(String pem) {
    try {
      byte[] der = decodePem(pem, "PRIVATE KEY");
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("JWT 개인키(PKCS#8 PEM) 파싱에 실패했습니다.", e);
    }
  }

  private static PublicKey parsePublicKey(String pem) {
    try {
      byte[] der = decodePem(pem, "PUBLIC KEY");
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("JWT 공개키(X.509 PEM) 파싱에 실패했습니다.", e);
    }
  }

  private static byte[] decodePem(String pem, String label) {
    if (pem == null || pem.isBlank()) {
      throw new IllegalStateException(
          "JWT 서명 키가 설정되지 않았습니다. LINA_JWT_PRIVATE_KEY/LINA_JWT_PUBLIC_KEY 환경변수를 주입하세요.");
    }
    String body =
        pem.replace("-----BEGIN " + label + "-----", "")
            .replace("-----END " + label + "-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}
