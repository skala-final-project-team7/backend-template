package com.lina.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class RagClientConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(RagClientConfig.class)
          .withPropertyValues(
              "lina.rag.base-url=http://rag.example",
              "lina.rag.request-timeout-ms=15000",
              "lina.rag.sse-timeout-ms=60000",
              "lina.data-ingestion.base-url=http://ingest.example",
              "lina.data-ingestion.request-timeout-ms=20000");

  @Test
  @DisplayName("ragRestClient 빈이 등록된다")
  void shouldRegisterRagRestClientBean() {
    runner.run(
        context -> {
          assertThat(context).hasBean("ragRestClient");
          assertThat(context.getBean("ragRestClient")).isInstanceOf(RestClient.class);
        });
  }

  @Test
  @DisplayName("dataIngestionRestClient 빈이 등록된다")
  void shouldRegisterDataIngestionRestClientBean() {
    runner.run(
        context -> {
          assertThat(context).hasBean("dataIngestionRestClient");
          assertThat(context.getBean("dataIngestionRestClient")).isInstanceOf(RestClient.class);
        });
  }

  @Test
  @DisplayName("base-url 이 비어있어도 컨텍스트가 정상 로드된다 (지연 호출 정책)")
  void shouldLoadContextEvenWhenBaseUrlIsBlank() {
    new ApplicationContextRunner()
        .withUserConfiguration(RagClientConfig.class)
        .withPropertyValues(
            "lina.rag.base-url=",
            "lina.rag.request-timeout-ms=15000",
            "lina.rag.sse-timeout-ms=60000",
            "lina.data-ingestion.base-url=",
            "lina.data-ingestion.request-timeout-ms=20000")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
