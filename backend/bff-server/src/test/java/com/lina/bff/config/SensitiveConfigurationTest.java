package com.lina.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class SensitiveConfigurationTest {

  @Test
  @DisplayName("actuator 민감 endpoint 와 Confluence credential 설정 키를 노출하지 않는다")
  void shouldNotExposeSensitiveActuatorEndpointsOrConfluenceCredentialProperties()
      throws IOException {
    PropertySource<?> applicationYaml = applicationYaml();

    assertThat(applicationYaml.getProperty("management.endpoints.web.exposure.include").toString())
        .doesNotContain("env", "heapdump");
    assertThat(applicationYaml.containsProperty("lina.confluence.access-token")).isFalse();
    assertThat(applicationYaml.containsProperty("lina.confluence.cloud-id")).isFalse();
    assertThat(applicationYaml.containsProperty("lina.demo.fixed-user-id")).isFalse();
    assertThat(applicationYaml.containsProperty("lina.demo.fixed-groups")).isFalse();
    assertThat(applicationYaml.containsProperty("lina.demo.fixed-space-key")).isFalse();
    assertThat(applicationYaml.containsProperty("lina.demo.fixed-role")).isFalse();
  }

  private PropertySource<?> applicationYaml() throws IOException {
    return new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yml"))
        .getFirst();
  }
}
