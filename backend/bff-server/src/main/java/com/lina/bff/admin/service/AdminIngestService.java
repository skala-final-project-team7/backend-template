package com.lina.bff.admin.service;

import com.lina.bff.admin.client.AuthAdminKeyClient;
import com.lina.bff.admin.dto.AdminIngestRequest;
import com.lina.bff.admin.dto.AdminIngestResponse;
import com.lina.bff.admin.dto.AdminIngestStatusResponse;
import com.lina.bff.admin.dto.IngestJobCommand;
import com.lina.bff.admin.messaging.AdminIngestJobProducer;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class AdminIngestService {

  private final CurrentUserProvider currentUserProvider;
  private final AuthAdminKeyClient authAdminKeyClient;
  private final AdminIngestJobProducer jobProducer;
  private final Clock clock;
  // RestClient 빈이 여러 개라 필드명(dataIngestionRestClient)으로 빈을 해석한다(빈명 매칭).
  private final RestClient dataIngestionRestClient;

  public AdminIngestResponse startIngest(AdminIngestRequest request) {
    Instant startedAt = Instant.now(clock);
    String jobId = UUID.randomUUID().toString();
    String adminUserId = currentUserProvider.getUserId();
    String mode = request == null ? "full" : request.normalizedMode();

    authAdminKeyClient.activate(adminUserId, jobId);
    jobProducer.publish(new IngestJobCommand(jobId, adminUserId, mode, startedAt));
    return new AdminIngestResponse(jobId, "STARTED", startedAt);
  }

  /**
   * 수집 진행상태를 Data Ingestion Pipeline 의 {@code GET /ml/ingest/status/{jobId}} 로 중계한다.
   *
   * <p>워커가 잡을 픽업하기 전(레코드 미생성) 폴링은 ingestion 이 404 를 반환하므로, 갓 시작한 잡으로 보고 {@code STARTED} 를 응답해 FE
   * 폴링이 끊기지 않게 한다.
   */
  public AdminIngestStatusResponse getIngestStatus(String jobId) {
    try {
      return dataIngestionRestClient
          .get()
          .uri("/ml/ingest/status/{jobId}", jobId)
          .retrieve()
          .body(AdminIngestStatusResponse.class);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return new AdminIngestStatusResponse(jobId, "STARTED", 0, 0, 0, null);
      }
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "수집 상태 조회에 실패했습니다", exception);
    } catch (RestClientException exception) {
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "수집 상태 조회에 실패했습니다", exception);
    }
  }
}
