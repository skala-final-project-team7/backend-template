package com.lina.bff.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestJobCommandTest {

  private static final Pattern FIELD_PATTERN = Pattern.compile("\"([^\"]+)\":");
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("ingest job payload schema 는 작업 식별/상태 필드만 포함한다")
  void shouldSerializeOnlyIngestJobSchemaFields() throws Exception {
    IngestJobCommand command =
        new IngestJobCommand(
            "job-1", "admin-account-id", "full", Instant.parse("2026-06-11T00:00:00Z"));

    String payload = objectMapper.writeValueAsString(command);
    Set<String> fieldNames = new HashSet<>();
    Matcher matcher = FIELD_PATTERN.matcher(payload);
    while (matcher.find()) {
      fieldNames.add(matcher.group(1));
    }

    assertThat(fieldNames)
        .containsExactlyInAnyOrder("jobId", "adminUserId", "mode", "requestedAt");
    assertThat(payload)
        .contains(
            "\"jobId\":\"job-1\"",
            "\"adminUserId\":\"admin-account-id\"",
            "\"mode\":\"full\"",
            "\"requestedAt\":\"2026-06-11T00:00:00Z\"");
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
