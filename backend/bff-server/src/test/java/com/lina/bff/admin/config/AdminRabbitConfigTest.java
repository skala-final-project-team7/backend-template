package com.lina.bff.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

class AdminRabbitConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(AdminRabbitConfig.class)
          .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
          .withPropertyValues(
              "lina.admin.ingest.rabbitmq.ingest-exchange=lina.admin.ingest",
              "lina.admin.ingest.rabbitmq.ingest-routing-key=admin.ingest.requested",
              "lina.admin.ingest.rabbitmq.ingest-queue=lina.data-ingestion.ingest",
              "lina.admin.ingest.rabbitmq.ingest-dlq=lina.data-ingestion.ingest.dlq",
              "lina.admin.ingest.rabbitmq.completion-queue=lina.admin.ingest.completion",
              "lina.admin.ingest.rabbitmq.completion-dlq=lina.admin.ingest.completion.dlq",
              "lina.admin.ingest.rabbitmq.completion-retry-max-attempts=5",
              "lina.admin.ingest.rabbitmq.completion-retry-initial-interval-ms=1000",
              "lina.admin.ingest.rabbitmq.completion-retry-multiplier=2.0",
              "lina.admin.ingest.rabbitmq.completion-retry-max-interval-ms=10000");

  @Test
  @DisplayName("completion queue 와 DLQ 를 durable queue 로 등록한다")
  void shouldRegisterDurableCompletionQueueAndDlq() {
    runner.run(
        context -> {
          Queue completionQueue = context.getBean("adminIngestCompletionQueue", Queue.class);
          Queue ingestQueue = context.getBean("adminIngestQueue", Queue.class);
          Queue dlq = context.getBean("adminIngestCompletionDlq", Queue.class);
          Queue ingestDlq = context.getBean("adminIngestDlq", Queue.class);

          assertThat(completionQueue.isDurable()).isTrue();
          assertThat(completionQueue.getName()).isEqualTo("lina.admin.ingest.completion");
          assertThat(completionQueue.getArguments())
              .containsEntry("x-dead-letter-routing-key", "lina.admin.ingest.completion.dlq");
          assertThat(dlq.isDurable()).isTrue();
          assertThat(dlq.getName()).isEqualTo("lina.admin.ingest.completion.dlq");
          assertThat(ingestQueue.isDurable()).isTrue();
          assertThat(ingestQueue.getName()).isEqualTo("lina.data-ingestion.ingest");
          assertThat(ingestQueue.getArguments())
              .containsEntry("x-dead-letter-routing-key", "lina.data-ingestion.ingest.dlq");
          assertThat(ingestDlq.isDurable()).isTrue();
          assertThat(ingestDlq.getName()).isEqualTo("lina.data-ingestion.ingest.dlq");
        });
  }

  @Test
  @DisplayName("completion listener 에 retry/backoff 및 reject recoverer 를 적용한다")
  void shouldRegisterCompletionRetryInterceptorAndListenerFactory() {
    runner.run(
        context -> {
          AdminIngestRabbitProperties properties =
              context.getBean(AdminIngestRabbitProperties.class);

          assertThat(properties.completionRetryMaxAttempts()).isEqualTo(5);
          assertThat(properties.completionRetryInitialIntervalMs()).isEqualTo(1000);
          assertThat(properties.completionRetryMultiplier()).isEqualTo(2.0);
          assertThat(properties.completionRetryMaxIntervalMs()).isEqualTo(10000);
          assertThat(context).hasSingleBean(RetryOperationsInterceptor.class);
          assertThat(context).hasSingleBean(RejectAndDontRequeueRecoverer.class);
          assertThat(context.getBean("rabbitListenerContainerFactory"))
              .isInstanceOf(SimpleRabbitListenerContainerFactory.class);
        });
  }
}
