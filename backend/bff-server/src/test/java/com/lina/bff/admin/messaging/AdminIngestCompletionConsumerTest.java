package com.lina.bff.admin.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.client.AuthAdminKeyClient.AuthAdminKeyException;
import com.lina.bff.admin.dto.IngestCompletionEvent;
import com.lina.bff.admin.messaging.AdminIngestCompletionConsumer.InvalidCompletionEventException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminIngestCompletionConsumerTest {

  private final AuthAdminKeyClient authAdminKeyClient = Mockito.mock(AuthAdminKeyClient.class);
  private final AdminIngestCompletionConsumer consumer =
      new AdminIngestCompletionConsumer(authAdminKeyClient);

  @Test
  @DisplayName("COMPLETED completion event 를 수신하면 Admin Key deactivate 를 호출한다")
  void shouldDeactivateAdminKeyWhenCompletedEventReceived() {
    IngestCompletionEvent event =
        new IngestCompletionEvent(
            "job-1", "admin-account-id", "full", "COMPLETED", Instant.now(), null, "done");

    consumer.consume(event);

    verify(authAdminKeyClient).deactivate("admin-account-id", "job-1");
  }

  @Test
  @DisplayName("FAILED completion event 를 수신하면 Admin Key deactivate 를 호출한다")
  void shouldDeactivateAdminKeyWhenFailedEventReceived() {
    IngestCompletionEvent event =
        new IngestCompletionEvent(
            "job-1",
            "admin-account-id",
            "full",
            "FAILED",
            Instant.now(),
            "INGEST_FAILED",
            "failed");

    consumer.consume(event);

    verify(authAdminKeyClient).deactivate("admin-account-id", "job-1");
  }

  @Test
  @DisplayName("완료/실패가 아닌 completion event 는 Admin Key deactivate 를 호출하지 않는다")
  void shouldIgnoreNonTerminalEventStatus() {
    IngestCompletionEvent event =
        new IngestCompletionEvent(
            "job-1", "admin-account-id", "full", "IN_PROGRESS", Instant.now(), null, null);

    consumer.consume(event);

    verifyNoInteractions(authAdminKeyClient);
  }

  @Test
  @DisplayName("Admin Key deactivate 실패 시 예외를 전파해 RabbitMQ retry/DLQ 경계로 넘긴다")
  void shouldPropagateDeactivateFailureForRetryOrDlqBoundary() {
    IngestCompletionEvent event =
        new IngestCompletionEvent(
            "job-1", "admin-account-id", "full", "COMPLETED", Instant.now(), null, "done");
    doThrow(new AuthAdminKeyException("Failed to deactivate Admin Key", new RuntimeException()))
        .when(authAdminKeyClient)
        .deactivate("admin-account-id", "job-1");

    assertThatThrownBy(() -> consumer.consume(event)).isInstanceOf(AuthAdminKeyException.class);

    verify(authAdminKeyClient).deactivate("admin-account-id", "job-1");
  }

  @Test
  @DisplayName("jobId 가 누락된 completion event 는 Admin Key deactivate 를 호출하지 않고 실패 처리한다")
  void shouldRejectCompletionEventWithoutJobId() {
    IngestCompletionEvent event =
        new IngestCompletionEvent(
            null, "admin-account-id", "full", "COMPLETED", Instant.now(), null, "done");

    assertThatThrownBy(() -> consumer.consume(event))
        .isInstanceOf(InvalidCompletionEventException.class)
        .hasMessage("completion event jobId is required");

    verifyNoInteractions(authAdminKeyClient);
  }

  @Test
  @DisplayName("adminUserId 가 누락된 completion event 는 Admin Key deactivate 를 호출하지 않고 실패 처리한다")
  void shouldRejectCompletionEventWithoutAdminUserId() {
    IngestCompletionEvent event =
        new IngestCompletionEvent("job-1", " ", "full", "FAILED", Instant.now(), null, "failed");

    assertThatThrownBy(() -> consumer.consume(event))
        .isInstanceOf(InvalidCompletionEventException.class)
        .hasMessage("completion event adminUserId is required");

    verifyNoInteractions(authAdminKeyClient);
  }
}
