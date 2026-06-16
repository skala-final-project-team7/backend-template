package com.lina.bff.admin.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
@EnableConfigurationProperties(AdminIngestRabbitProperties.class)
public class AdminRabbitConfig {

  @Bean
  DirectExchange adminIngestExchange(AdminIngestRabbitProperties properties) {
    return new DirectExchange(properties.ingestExchange(), true, false);
  }

  @Bean
  Queue adminIngestCompletionQueue(AdminIngestRabbitProperties properties) {
    return QueueBuilder.durable(properties.completionQueue())
        .deadLetterExchange("")
        .deadLetterRoutingKey(properties.completionDlq())
        .build();
  }

  @Bean
  Queue adminIngestCompletionDlq(AdminIngestRabbitProperties properties) {
    return QueueBuilder.durable(properties.completionDlq()).build();
  }

  @Bean
  Queue adminIngestQueue(AdminIngestRabbitProperties properties) {
    return QueueBuilder.durable(properties.ingestQueue())
        .deadLetterExchange("")
        .deadLetterRoutingKey(properties.ingestDlq())
        .build();
  }

  @Bean
  Queue adminIngestDlq(AdminIngestRabbitProperties properties) {
    return QueueBuilder.durable(properties.ingestDlq()).build();
  }

  @Bean
  RejectAndDontRequeueRecoverer adminCompletionMessageRecoverer() {
    return new RejectAndDontRequeueRecoverer();
  }

  @Bean
  RetryOperationsInterceptor adminCompletionRetryInterceptor(
      AdminIngestRabbitProperties properties, RejectAndDontRequeueRecoverer recoverer) {
    return RetryInterceptorBuilder.stateless()
        .maxAttempts(properties.completionRetryMaxAttempts())
        .backOffOptions(
            properties.completionRetryInitialIntervalMs(),
            properties.completionRetryMultiplier(),
            properties.completionRetryMaxIntervalMs())
        .recoverer(recoverer)
        .build();
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      MessageConverter adminRabbitMessageConverter,
      RetryOperationsInterceptor adminCompletionRetryInterceptor) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(adminRabbitMessageConverter);
    factory.setAdviceChain(adminCompletionRetryInterceptor);
    return factory;
  }

  @Bean
  MessageConverter adminRabbitMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
