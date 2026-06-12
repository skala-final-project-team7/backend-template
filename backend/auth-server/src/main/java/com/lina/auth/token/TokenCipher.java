package com.lina.auth.token;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 토큰 컬럼 AES-GCM 암호화 AttributeConverter. Confluence OAuth access/refresh,
 *           admin API Token 을 평문 저장 금지 규칙(backend/rules/auth.md §2)에 따라 암호화한다.
 *           키는 env(LINA_TOKEN_ENCRYPTION_KEY) 주입 — 미설정 시 부팅 단계에서 fail-fast.
 *           저장 포맷 = IV(12바이트) || GCM 암호문(+128bit 태그).
 * 작성일 : 2026-06-11
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-11, 최초 작성, 3단계 Feature 1 — user_tokens/admin_atlassian_credential 암호화 컬럼
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS (javax.crypto AES/GCM/NoPadding)
 *   - Spring Boot 3.3.x / Hibernate 6.5.x (SpringBeanContainer 로 빈 주입 converter 사용)
 * --------------------------------------------------
 * </pre>
 */
@Component
@Converter
public class TokenCipher implements AttributeConverter<String, byte[]> {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(@Value("${lina.token-encryption.key}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException(
          "토큰 암호화 키가 설정되지 않았습니다. LINA_TOKEN_ENCRYPTION_KEY 환경변수를 주입하세요.");
    }
    this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
  }

  @Override
  public byte[] convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

      byte[] result = new byte[IV_LENGTH_BYTES + encrypted.length];
      System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
      System.arraycopy(encrypted, 0, result, IV_LENGTH_BYTES, encrypted.length);
      return result;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("토큰 암호화에 실패했습니다.", e);
    }
  }

  @Override
  public String convertToEntityAttribute(byte[] dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      byte[] iv = Arrays.copyOfRange(dbData, 0, IV_LENGTH_BYTES);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] decrypted = cipher.doFinal(dbData, IV_LENGTH_BYTES, dbData.length - IV_LENGTH_BYTES);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("토큰 복호화에 실패했습니다. 암호화 키 또는 저장 값이 올바르지 않습니다.", e);
    }
  }
}
