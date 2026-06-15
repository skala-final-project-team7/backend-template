package com.lina.bff.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * auth-server 가 발급한 LINA access JWT 를 RS256 공개키로 검증한다.
 *
 * <p>공개키 파싱은 첫 검증 시점까지 지연한다. 로컬/test 환경에서 public key 가 비어 있어도 애플리케이션은 부팅되고, 보호 API 호출 시 인증 실패로
 * 처리된다.
 */
@Component
public class BffJwtVerifier {

  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_GROUPS = "groups";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TOKEN_TYPE = "tokenType";

  private final BffJwtProperties properties;
  private volatile PublicKey publicKey;

  public BffJwtVerifier(BffJwtProperties properties) {
    this.properties = properties;
  }

  public BffJwtClaims verifyAccessToken(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(publicKey())
            .requireIssuer(properties.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    if (claims.get(CLAIM_TOKEN_TYPE) != null) {
      throw new JwtException("access 검증에 사용할 수 없는 토큰 타입입니다.");
    }
    List<?> rawGroups = claims.get(CLAIM_GROUPS, List.class);
    List<String> groups =
        rawGroups == null ? List.of() : rawGroups.stream().map(String::valueOf).toList();
    return new BffJwtClaims(
        claims.get(CLAIM_USER_ID, String.class), groups, claims.get(CLAIM_ROLE, String.class));
  }

  private PublicKey publicKey() {
    PublicKey cached = publicKey;
    if (cached != null) {
      return cached;
    }
    synchronized (this) {
      if (publicKey == null) {
        publicKey = parsePublicKey(properties.getPublicKey());
      }
      return publicKey;
    }
  }

  private static PublicKey parsePublicKey(String pem) {
    try {
      byte[] der = decodePem(pem);
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new JwtException("JWT 공개키(X.509 PEM) 파싱에 실패했습니다.", exception);
    }
  }

  private static byte[] decodePem(String pem) {
    if (pem == null || pem.isBlank()) {
      throw new JwtException("JWT 공개키가 설정되지 않았습니다.");
    }
    String body =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}
