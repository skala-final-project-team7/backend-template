package com.lina.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BffJwtVerifierTest {

  @Test
  @DisplayName("auth-server access JWT claim 을 userId/groups/role 로 검증한다")
  void shouldVerifyAccessTokenClaims() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    BffJwtVerifier verifier =
        new BffJwtVerifier(new BffJwtProperties("lina-auth-server", publicKeyPem(keyPair)));
    String token =
        Jwts.builder()
            .issuer("lina-auth-server")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("userId", "712020:abc")
            .claim("groups", List.of("group-id-1", "group-id-2"))
            .claim("role", "ADMIN")
            .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();

    BffJwtClaims claims = verifier.verifyAccessToken(token);

    assertThat(claims.userId()).isEqualTo("712020:abc");
    assertThat(claims.groups()).containsExactly("group-id-1", "group-id-2");
    assertThat(claims.role()).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("refresh token claim tokenType 이 있으면 access JWT 로 거부한다")
  void shouldRejectRefreshTokenType() throws Exception {
    KeyPair keyPair = generateRsaKeyPair();
    BffJwtVerifier verifier =
        new BffJwtVerifier(new BffJwtProperties("lina-auth-server", publicKeyPem(keyPair)));
    String token =
        Jwts.builder()
            .issuer("lina-auth-server")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .claim("userId", "712020:abc")
            .claim("tokenType", "refresh")
            .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();

    assertThatThrownBy(() -> verifier.verifyAccessToken(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("공개키가 비어 있으면 검증 시점에 JwtException 으로 실패한다")
  void shouldFailWhenPublicKeyIsBlank() {
    BffJwtVerifier verifier = new BffJwtVerifier(new BffJwtProperties("lina-auth-server", ""));

    assertThatThrownBy(() -> verifier.verifyAccessToken("invalid-token"))
        .isInstanceOf(JwtException.class);
  }

  private static KeyPair generateRsaKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String publicKeyPem(KeyPair keyPair) {
    String body =
        Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
  }
}
