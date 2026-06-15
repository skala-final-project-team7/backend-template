package com.lina.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Feature 7 — 운영 application.yml 의 민감 설정이 평문이 아니라 ${ENV} 주입인지, actuator 민감 엔드포인트가 노출 목록에서 제외되는지를
 * 고정한다.
 *
 * <p>운영 리소스(src/main/resources/application.yml)를 그대로 로드해 검사한다. PropertySource 단계라 placeholder 가
 * 해석되지 않으므로 원문(${...})을 그대로 확인할 수 있다 — 평문 secret 이 끼어들면 즉시 깨진다.
 */
class SensitiveConfigurationTest {

  private static final PropertySource<?> APPLICATION_YML = loadApplicationYml();

  private static PropertySource<?> loadApplicationYml() {
    try {
      List<PropertySource<?>> sources =
          new YamlPropertySourceLoader()
              .load("application.yml", new ClassPathResource("application.yml"));
      return sources.get(0);
    } catch (IOException e) {
      throw new IllegalStateException("application.yml 로드 실패", e);
    }
  }

  private static String raw(String key) {
    Object value = APPLICATION_YML.getProperty(key);
    return value == null ? null : value.toString();
  }

  private static void assertInjectedSecret(String key, String envVar) {
    assertThat(raw(key))
        .as("%s 는 평문이 아니라 ${%s} 환경변수 주입이어야 한다", key, envVar)
        .isNotNull()
        .startsWith("${")
        .contains(envVar);
  }

  @Test
  @DisplayName("OAuth client-secret 은 ${CONFLUENCE_CLIENT_SECRET} 주입이다")
  void oauthClientSecretIsInjected() {
    assertInjectedSecret("lina.oauth.confluence.client-secret", "CONFLUENCE_CLIENT_SECRET");
  }

  @Test
  @DisplayName("DB 접속정보(username/password)는 ${MYSQL_*} 주입이다")
  void datasourceCredentialsAreInjected() {
    assertInjectedSecret("spring.datasource.password", "MYSQL_PASSWORD");
    assertInjectedSecret("spring.datasource.username", "MYSQL_USERNAME");
  }

  @Test
  @DisplayName("토큰 암호화 키는 ${LINA_TOKEN_ENCRYPTION_KEY} 주입이다")
  void tokenEncryptionKeyIsInjected() {
    assertInjectedSecret("lina.token-encryption.key", "LINA_TOKEN_ENCRYPTION_KEY");
  }

  @Test
  @DisplayName("JWT 서명 키(private/public)는 ${LINA_JWT_*} 주입이다")
  void jwtKeysAreInjected() {
    assertInjectedSecret("lina.jwt.private-key", "LINA_JWT_PRIVATE_KEY");
    assertInjectedSecret("lina.jwt.public-key", "LINA_JWT_PUBLIC_KEY");
  }

  @Test
  @DisplayName("내부 호출자 키·admin seed api-token 은 ${...} 주입이다")
  void internalAndAdminSecretsAreInjected() {
    assertInjectedSecret("lina.internal.api-key", "INTERNAL_API_KEY");
    assertInjectedSecret("lina.admin-seed.api-token", "LINA_ADMIN_API_TOKEN");
  }

  @Test
  @DisplayName("actuator 노출 허용목록(include)에 민감 엔드포인트가 없다")
  void sensitiveActuatorEndpointsAreNotIncluded() {
    String include = raw("management.endpoints.web.exposure.include");
    assertThat(include).isNotNull();
    assertThat(include)
        .as("include 허용목록에 민감 엔드포인트가 있으면 안 된다: %s", include)
        .doesNotContain("env")
        .doesNotContain("heapdump")
        .doesNotContain("threaddump")
        .doesNotContain("beans");
  }

  @Test
  @DisplayName("actuator exclude 로 env/heapdump/threaddump 를 명시 차단한다(include 가 넓어져도 비노출)")
  void sensitiveActuatorEndpointsAreExcluded() {
    String exclude = raw("management.endpoints.web.exposure.exclude");
    assertThat(exclude).as("exclude 가 include 보다 우선 — 민감 엔드포인트는 명시적으로 제외해야 한다").isNotNull();
    assertThat(exclude).contains("env").contains("heapdump").contains("threaddump");
  }
}
