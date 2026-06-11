package com.lina.bff.admin.messaging;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.dto.IngestCompletionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminIngestCompletionConsumer {

  private static final String COMPLETED = "COMPLETED";
  private static final String FAILED = "FAILED";

  private final AuthAdminKeyClient authAdminKeyClient;

  @RabbitListener(queues = "${lina.admin.ingest.rabbitmq.completion-queue}")
  public void consume(IngestCompletionEvent event) {
    if (!isTerminalStatus(event.status())) {
      return;
    }

    authAdminKeyClient.deactivate(event.adminUserId(), event.jobId());
  }

  private boolean isTerminalStatus(String status) {
    return COMPLETED.equals(status) || FAILED.equals(status);
  }
}
