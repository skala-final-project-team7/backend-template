package com.lina.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

  private static final String ISSUER = "lina-auth-server";
  private static final long ACCESS_TTL_SECONDS = 3600;
  private static final long REFRESH_TTL_SECONDS = 1209600;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static String privatePem;
  private static String publicPem;
  private static String otherPrivatePem;

  @BeforeAll
  static void generateKeyPairs() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    privatePem = toPem(keyPair.getPrivate().getEncoded(), "PRIVATE KEY");
    publicPem = toPem(keyPair.getPublic().getEncoded(), "PUBLIC KEY");
    otherPrivatePem = toPem(generator.generateKeyPair().getPrivate().getEncoded(), "PRIVATE KEY");
  }

  private static String toPem(byte[] der, String label) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getEncoder().encodeToString(der)
        + "\n-----END "
        + label
        + "-----";
  }

  private static JwtProvider provider() {
    return provider(ISSUER, ACCESS_TTL_SECONDS, REFRESH_TTL_SECONDS);
  }

  private static JwtProvider provider(String issuer, long accessTtl, long refreshTtl) {
    return new JwtProvider(new JwtProperties(issuer, privatePem, publicPem, accessTtl, refreshTtl));
  }

  private static JwtClaims claims() {
    return new JwtClaims("712020:abc", List.of("group-1", "group-2"), "ADMIN");
  }

  private static JsonNode decodePayload(String token) {
    try {
      String payload = token.split("\\.")[1];
      return OBJECT_MAPPER.readTree(
          new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("토큰 payload 디코딩 실패", e);
    }
  }

  @Test
  @DisplayName("access JWT 발급→검증 라운드트립에서 userId/groups/role claim 이 보존된다")
  void shouldRoundTripAccessTokenClaims() {
    JwtProvider provider = provider();

    JwtClaims verified = provider.verifyAccessToken(provider.issueAccessToken(claims()));

    assertThat(verified.userId()).isEqualTo("712020:abc");
    assertThat(verified.groups()).containsExactly("group-1", "group-2");
    assertThat(verified.role()).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("와이어 claim 이름이 camelCase 계약(userId/groups/role/iss/iat/exp)과 일치한다")
  void shouldUseCamelCaseWireClaimNames() {
    JsonNode payload = decodePayload(provider().issueAccessToken(claims()));

    assertThat(payload.has("userId")).isTrue();
    assertThat(payload.has("groups")).isTrue();
    assertThat(payload.has("role")).isTrue();
    assertThat(payload.get("iss").asText()).isEqualTo(ISSUER);
    assertThat(payload.has("iat")).isTrue();
    assertThat(payload.has("exp")).isTrue();
  }

  @Test
  @DisplayName("access·refresh 만료 시각이 설정 TTL 대로 발급된다(exp - iat == TTL)")
  void shouldSetExpiryFromConfiguredTtl() {
    JwtProvider provider = provider();

    JsonNode access = decodePayload(provider.issueAccessToken(claims()));
    JsonNode refresh = decodePayload(provider.issueRefreshToken("712020:abc"));

    assertThat(access.get("exp").asLong() - access.get("iat").asLong())
        .isEqualTo(ACCESS_TTL_SECONDS);
    assertThat(refresh.get("exp").asLong() - refresh.get("iat").asLong())
        .isEqualTo(REFRESH_TTL_SECONDS);
  }

  @Test
  @DisplayName("groups 빈 배열도 그대로 보존된다(빈 멤버십 허용)")
  void shouldAllowEmptyGroups() {
    JwtProvider provider = provider();

    JwtClaims verified =
        provider.verifyAccessToken(
            provider.issueAccessToken(new JwtClaims("712020:abc", List.of(), "USER")));

    assertThat(verified.groups()).isEmpty();
  }

  @Test
  @DisplayName("만료된 access JWT 는 검증을 거부한다")
  void shouldRejectExpiredAccessToken() {
    String expired = provider(ISSUER, -60, REFRESH_TTL_SECONDS).issueAccessToken(claims());

    assertThatThrownBy(() -> provider().verifyAccessToken(expired))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  @DisplayName("다른 개인키로 서명된 토큰(서명 위조)은 검증을 거부한다")
  void shouldRejectForgedSignature() {
    JwtProvider forger =
        new JwtProvider(
            new JwtProperties(
                ISSUER, otherPrivatePem, publicPem, ACCESS_TTL_SECONDS, REFRESH_TTL_SECONDS));
    String forged = forger.issueAccessToken(claims());

    assertThatThrownBy(() -> provider().verifyAccessToken(forged))
        .isInstanceOf(SignatureException.class);
  }

  @Test
  @DisplayName("issuer 가 다른 토큰은 검증을 거부한다")
  void shouldRejectWrongIssuer() {
    String wrongIssuer =
        provider("other-issuer", ACCESS_TTL_SECONDS, REFRESH_TTL_SECONDS)
            .issueAccessToken(claims());

    assertThatThrownBy(() -> provider().verifyAccessToken(wrongIssuer))
        .isInstanceOf(IncorrectClaimException.class);
  }

  @Test
  @DisplayName("refresh token 발급→검증 라운드트립에서 userId 가 회수된다")
  void shouldRoundTripRefreshToken() {
    JwtProvider provider = provider();

    assertThat(provider.verifyRefreshToken(provider.issueRefreshToken("712020:abc")))
        .isEqualTo("712020:abc");
  }

  @Test
  @DisplayName("refresh token 을 access 검증에 사용하면 거부한다")
  void shouldRejectRefreshTokenForAccessVerification() {
    JwtProvider provider = provider();
    String refresh = provider.issueRefreshToken("712020:abc");

    assertThatThrownBy(() -> provider.verifyAccessToken(refresh)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("access token 을 refresh 검증에 사용하면 거부한다")
  void shouldRejectAccessTokenForRefreshVerification() {
    JwtProvider provider = provider();
    String access = provider.issueAccessToken(claims());

    assertThatThrownBy(() -> provider.verifyRefreshToken(access))
        .isInstanceOf(MissingClaimException.class);
  }

  @Test
  @DisplayName("서명 키가 비어 있으면 생성 시점에 실패한다(env 미주입 fail-fast)")
  void shouldFailFastWhenKeyMissing() {
    assertThatThrownBy(
            () ->
                new JwtProvider(
                    new JwtProperties(
                        ISSUER, " ", publicPem, ACCESS_TTL_SECONDS, REFRESH_TTL_SECONDS)))
        .isInstanceOf(IllegalStateException.class);
  }
}
