package com.lina.bff.admin.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.dto.IngestCompletionEvent;
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
}
