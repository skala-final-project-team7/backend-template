package com.lina.bff.admin.messaging;

import com.lina.bff.admin.config.AdminIngestRabbitProperties;
import com.lina.bff.admin.dto.IngestJobCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminIngestJobProducer {

  private final RabbitTemplate rabbitTemplate;
  private final AdminIngestRabbitProperties properties;

  public void publish(IngestJobCommand command) {
    rabbitTemplate.convertAndSend(
        properties.ingestExchange(), properties.ingestRoutingKey(), command);
  }
}
