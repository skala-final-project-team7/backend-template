package com.lina.bff.admin.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminIngestRabbitProperties.class)
public class AdminRabbitConfig {

  @Bean
  DirectExchange adminIngestExchange(AdminIngestRabbitProperties properties) {
    return new DirectExchange(properties.ingestExchange(), true, false);
  }

  @Bean
  MessageConverter adminRabbitMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
