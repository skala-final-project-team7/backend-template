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
    validateRequiredFields(event);

    if (!isTerminalStatus(event.status())) {
      return;
    }

    authAdminKeyClient.deactivate(event.adminUserId(), event.jobId());
  }

  private void validateRequiredFields(IngestCompletionEvent event) {
    if (!hasText(event.jobId())) {
      throw new InvalidCompletionEventException("completion event jobId is required");
    }
    if (!hasText(event.adminUserId())) {
      throw new InvalidCompletionEventException("completion event adminUserId is required");
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private boolean isTerminalStatus(String status) {
    return COMPLETED.equals(status) || FAILED.equals(status);
  }

  public static class InvalidCompletionEventException extends RuntimeException {

    public InvalidCompletionEventException(String message) {
      super(message);
    }
  }
}
