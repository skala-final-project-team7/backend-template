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

  private static final List<PropertySource<?>> APPLICATION_YMLS = loadApplicationYmls();

  private static List<PropertySource<?>> loadApplicationYmls() {
    try {
      return new YamlPropertySourceLoader()
          .load("application", new ClassPathResource("application.yml"));
    } catch (IOException e) {
      throw new IllegalStateException("application.yml 로드 실패", e);
    }
  }

  private static String raw(String key) {
    for (PropertySource<?> source : APPLICATION_YMLS) {
      Object value = source.getProperty(key);
      if (value != null) {
        return value.toString();
      }
    }
    return null;
  }

  private static boolean hasAnyProperty(String key) {
    for (PropertySource<?> source : APPLICATION_YMLS) {
      if (source.containsProperty(key)) {
        return true;
      }
    }
    return false;
  }

  private static void assertPropertyNotContainsSensitiveValue(String key) {
    String value = raw(key);
    assertThat(value)
        .as("%s 값이 null 이면 Yaml 파싱/로드 방식 검증이 실패했습니다.", key)
        .isNotNull();
    assertThat(value).doesNotContain("env", "heapdump", "threaddump", "beans");
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
    assertPropertyNotContainsSensitiveValue("management.endpoints.web.exposure.include");
  }

  @Test
  @DisplayName("actuator exclude 로 env/heapdump/threaddump 를 명시 차단한다(include 가 넓어져도 비노출)")
  void sensitiveActuatorEndpointsAreExcluded() {
    assertThat(hasAnyProperty("management.endpoints.web.exposure.exclude"))
        .as("actuator 제외 목록 키가 설정되어 있어야 합니다")
        .isTrue();
    String exclude = raw("management.endpoints.web.exposure.exclude");
    assertThat(exclude)
        .as("exclude 가 include 보다 우선 — 민감 엔드포인트는 명시적으로 제외해야 한다")
        .contains("env")
        .contains("heapdump")
        .contains("threaddump");
  }
}
