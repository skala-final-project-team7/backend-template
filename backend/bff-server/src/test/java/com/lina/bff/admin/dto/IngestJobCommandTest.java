package com.lina.bff.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestJobCommandTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("ingest job payload schema 는 작업 식별/상태 필드만 포함한다")
  void shouldSerializeOnlyIngestJobSchemaFields() throws Exception {
    IngestJobCommand command =
        new IngestJobCommand(
            "job-1", "admin-account-id", "full", Instant.parse("2026-06-11T00:00:00Z"));

    var payload = objectMapper.readTree(objectMapper.writeValueAsString(command));

    assertThat(payload.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("jobId", "adminUserId", "mode", "requestedAt");
    assertThat(payload.get("jobId").asText()).isEqualTo("job-1");
    assertThat(payload.get("adminUserId").asText()).isEqualTo("admin-account-id");
    assertThat(payload.get("mode").asText()).isEqualTo("full");
    assertThat(payload.get("requestedAt").asText()).isEqualTo("2026-06-11T00:00:00Z");
  }

  @Test
  @DisplayName("ingest job payload 에 Confluence credential 필드를 포함하지 않는다")
  void shouldNotContainConfluenceCredentialFields() throws Exception {
    IngestJobCommand command =
        new IngestJobCommand(
            "job-1", "admin-account-id", "full", Instant.parse("2026-06-11T00:00:00Z"));

    String payload = objectMapper.writeValueAsString(command);

    assertThat(payload).doesNotContain("accessToken", "refreshToken", "cloudId");
  }
}
