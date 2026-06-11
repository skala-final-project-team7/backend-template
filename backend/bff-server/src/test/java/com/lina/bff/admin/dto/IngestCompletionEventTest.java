package com.lina.bff.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestCompletionEventTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("completion event payload schema 는 작업 완료/실패 식별 필드로 고정한다")
  void shouldSerializeOnlyCompletionEventSchemaFields() throws Exception {
    IngestCompletionEvent event =
        new IngestCompletionEvent(
            "job-1",
            "admin-account-id",
            "full",
            "COMPLETED",
            Instant.parse("2026-06-11T00:05:00Z"),
            null,
            "done");

    var payload = objectMapper.readTree(objectMapper.writeValueAsString(event));

    assertThat(payload.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "jobId", "adminUserId", "mode", "status", "completedAt", "errorCode", "message");
    assertThat(payload.get("jobId").asText()).isEqualTo("job-1");
    assertThat(payload.get("adminUserId").asText()).isEqualTo("admin-account-id");
    assertThat(payload.get("mode").asText()).isEqualTo("full");
    assertThat(payload.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(payload.get("completedAt").asText()).isEqualTo("2026-06-11T00:05:00Z");
    assertThat(payload.get("message").asText()).isEqualTo("done");
  }

  @Test
  @DisplayName("completion event JSON 을 DTO 로 역직렬화한다")
  void shouldDeserializeCompletionEventPayload() throws Exception {
    String payload =
        """
        {
          "jobId": "job-1",
          "adminUserId": "admin-account-id",
          "mode": "full",
          "status": "FAILED",
          "completedAt": "2026-06-11T00:05:00Z",
          "errorCode": "INGEST_FAILED",
          "message": "failed"
        }
        """;

    IngestCompletionEvent event = objectMapper.readValue(payload, IngestCompletionEvent.class);

    assertThat(event.jobId()).isEqualTo("job-1");
    assertThat(event.adminUserId()).isEqualTo("admin-account-id");
    assertThat(event.mode()).isEqualTo("full");
    assertThat(event.status()).isEqualTo("FAILED");
    assertThat(event.completedAt()).isEqualTo(Instant.parse("2026-06-11T00:05:00Z"));
    assertThat(event.errorCode()).isEqualTo("INGEST_FAILED");
    assertThat(event.message()).isEqualTo("failed");
  }
}
