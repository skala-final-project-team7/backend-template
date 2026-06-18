package com.lina.bff.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.dto.AdminIngestRequest;
import com.lina.bff.admin.dto.IngestJobCommand;
import com.lina.bff.admin.messaging.AdminIngestJobProducer;
import com.lina.bff.config.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

class AdminIngestServiceTest {

  private final CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
  private final AuthAdminKeyClient authAdminKeyClient = Mockito.mock(AuthAdminKeyClient.class);
  private final AdminIngestJobProducer jobProducer = Mockito.mock(AdminIngestJobProducer.class);
  private final RestClient dataIngestionRestClient = Mockito.mock(RestClient.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-11T00:00:00Z"), ZoneOffset.UTC);
  private final AdminIngestService service =
      new AdminIngestService(
          currentUserProvider, authAdminKeyClient, jobProducer, clock, dataIngestionRestClient);

  @Test
  @DisplayName("/api/admin/ingest 서비스는 jobId 생성, Admin Key 활성화, RabbitMQ job 발행을 수행한다")
  void shouldActivateAdminKeyAndPublishIngestJob() {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");

    var response = service.startIngest(new AdminIngestRequest("delta"));

    ArgumentCaptor<String> jobIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(authAdminKeyClient).activate(eq("admin-account-id"), jobIdCaptor.capture());

    ArgumentCaptor<IngestJobCommand> commandCaptor =
        ArgumentCaptor.forClass(IngestJobCommand.class);
    verify(jobProducer).publish(commandCaptor.capture());
    IngestJobCommand command = commandCaptor.getValue();

    assertThat(response.status()).isEqualTo("STARTED");
    assertThat(response.jobId()).isEqualTo(jobIdCaptor.getValue());
    assertThat(command.jobId()).isEqualTo(response.jobId());
    assertThat(command.adminUserId()).isEqualTo("admin-account-id");
    assertThat(command.mode()).isEqualTo("delta");
    assertThat(command.requestedAt()).isEqualTo(Instant.parse("2026-06-11T00:00:00Z"));
  }

  @Test
  @DisplayName("mode 를 생략하면 full ingest job 으로 발행한다")
  void shouldDefaultModeToFull() {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");

    service.startIngest(null);

    ArgumentCaptor<IngestJobCommand> commandCaptor =
        ArgumentCaptor.forClass(IngestJobCommand.class);
    verify(jobProducer).publish(commandCaptor.capture());
    assertThat(commandCaptor.getValue().mode()).isEqualTo("full");
  }

  @Test
  @DisplayName("Admin Key 활성화 실패 시 job 발행을 수행하지 않고 예외를 전파한다")
  void shouldNotPublishJobWhenActivationFails() {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    var ex =
        new AuthAdminKeyClient.AuthAdminKeyException(
            "Failed to activate Admin Key", new RuntimeException("boom"));

    org.mockito.Mockito.doThrow(ex)
        .when(authAdminKeyClient)
        .activate(eq("admin-account-id"), org.mockito.Mockito.any());

    assertThatThrownBy(() -> service.startIngest(new AdminIngestRequest("full")))
        .isInstanceOf(AuthAdminKeyClient.AuthAdminKeyException.class)
        .hasMessageContaining("Failed to activate Admin Key");

    verifyNoInteractions(jobProducer);
  }
}
