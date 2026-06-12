package com.lina.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenCipherTest {

  private static final String KEY =
      Base64.getEncoder()
          .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
  private static final String OTHER_KEY =
      Base64.getEncoder()
          .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

  private final TokenCipher cipher = new TokenCipher(KEY);

  @Test
  @DisplayName("암호화한 토큰을 복호화하면 원문이 복원된다")
  void shouldRoundTripPlaintext() {
    String plain = "atlassian-access-token-예시-1234567890";

    byte[] encrypted = cipher.convertToDatabaseColumn(plain);

    assertThat(cipher.convertToEntityAttribute(encrypted)).isEqualTo(plain);
  }

  @Test
  @DisplayName("암호문에 평문이 그대로 포함되지 않는다")
  void shouldNotContainPlaintextInCiphertext() {
    String plain = "plain-token-value";

    byte[] encrypted = cipher.convertToDatabaseColumn(plain);

    assertThat(encrypted).isNotEqualTo(plain.getBytes(StandardCharsets.UTF_8));
    assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain(plain);
  }

  @Test
  @DisplayName("동일 평문을 두 번 암호화하면 서로 다른 암호문이 생성된다(랜덤 IV)")
  void shouldProduceDifferentCiphertextForSamePlaintext() {
    String plain = "same-token";

    assertThat(cipher.convertToDatabaseColumn(plain))
        .isNotEqualTo(cipher.convertToDatabaseColumn(plain));
  }

  @Test
  @DisplayName("다른 키로 암호화된 값은 복호화를 거부한다")
  void shouldRejectCiphertextEncryptedWithDifferentKey() {
    byte[] encrypted = new TokenCipher(OTHER_KEY).convertToDatabaseColumn("secret-token");

    assertThatThrownBy(() -> cipher.convertToEntityAttribute(encrypted))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("암호화 키가 비어 있으면 생성 시점에 실패한다(env 미주입 fail-fast)")
  void shouldFailFastWhenKeyIsBlank() {
    assertThatThrownBy(() -> new TokenCipher(" ")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("null 값은 암복호화 없이 null 을 유지한다")
  void shouldPassThroughNull() {
    assertThat(cipher.convertToDatabaseColumn(null)).isNull();
    assertThat(cipher.convertToEntityAttribute(null)).isNull();
  }
}
