package com.lina.bff.admin.service;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.dto.AdminIngestRequest;
import com.lina.bff.admin.dto.AdminIngestResponse;
import com.lina.bff.admin.dto.IngestJobCommand;
import com.lina.bff.admin.messaging.AdminIngestJobProducer;
import com.lina.bff.config.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminIngestService {

  private final CurrentUserProvider currentUserProvider;
  private final AuthAdminKeyClient authAdminKeyClient;
  private final AdminIngestJobProducer jobProducer;
  private final Clock clock;

  public AdminIngestResponse startIngest(AdminIngestRequest request) {
    Instant startedAt = Instant.now(clock);
    String jobId = UUID.randomUUID().toString();
    String adminUserId = currentUserProvider.getUserId();
    String mode = request == null ? "full" : request.normalizedMode();

    authAdminKeyClient.activate(adminUserId, jobId);
    jobProducer.publish(new IngestJobCommand(jobId, adminUserId, mode, startedAt));
    return new AdminIngestResponse(jobId, "STARTED", startedAt);
  }
}
