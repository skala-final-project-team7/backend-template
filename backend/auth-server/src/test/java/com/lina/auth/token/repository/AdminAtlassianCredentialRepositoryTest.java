package com.lina.auth.token.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.auth.token.TokenCipher;
import com.lina.auth.token.entity.AdminAtlassianCredential;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(TokenCipher.class)
class AdminAtlassianCredentialRepositoryTest {

  @Autowired private AdminAtlassianCredentialRepository credentialRepository;
  @Autowired private TestEntityManager entityManager;

  private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  @DisplayName("site_url 과 admin API Token 이 저장 후 평문으로 복원된다(라운드트립)")
  void shouldRoundTripCredential() {
    credentialRepository.saveAndFlush(
        AdminAtlassianCredential.builder()
            .userKey(USER_KEY)
            .siteUrl("https://example.atlassian.net")
            .adminApiToken("admin-api-token-1")
            .build());
    entityManager.clear();

    AdminAtlassianCredential found = credentialRepository.findById(USER_KEY).orElseThrow();

    assertThat(found.getSiteUrl()).isEqualTo("https://example.atlassian.net");
    assertThat(found.getAdminApiToken()).isEqualTo("admin-api-token-1");
  }

  @Test
  @DisplayName("DB 에 저장된 admin API Token 컬럼 값은 평문이 아니다")
  void shouldNotStoreApiTokenAsPlaintext() {
    credentialRepository.saveAndFlush(
        AdminAtlassianCredential.builder()
            .userKey(USER_KEY)
            .siteUrl("https://example.atlassian.net")
            .adminApiToken("admin-api-token-1")
            .build());
    entityManager.clear();

    byte[] stored =
        (byte[])
            entityManager
                .getEntityManager()
                .createNativeQuery(
                    "select admin_api_token_enc from admin_atlassian_credential"
                        + " where user_key = ?")
                .setParameter(1, USER_KEY)
                .getSingleResult();

    assertThat(stored).isNotEqualTo("admin-api-token-1".getBytes(StandardCharsets.UTF_8));
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("admin-api-token-1");
  }
}
