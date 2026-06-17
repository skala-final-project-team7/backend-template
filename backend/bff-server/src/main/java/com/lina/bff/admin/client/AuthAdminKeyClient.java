package com.lina.bff.admin.client;

import com.lina.bff.admin.client.dto.AdminKeyActivateRequest;
import com.lina.bff.admin.client.dto.AdminKeyDeactivateRequest;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class AuthAdminKeyClient {

  private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

  private final RestClient authServerRestClient;

  @Value("${lina.internal.api-key:${RAG_INTERNAL_API_KEY:${INTERNAL_API_KEY:}}}")
  private String internalApiKey;

  public void activate(String adminUserId, String jobId) {
    try {
      authServerRestClient
          .post()
          .uri("/internal/admin/key/activate")
          .contentType(MediaType.APPLICATION_JSON)
          .header(INTERNAL_API_KEY_HEADER, internalApiKey == null ? "" : internalApiKey)
          .body(new AdminKeyActivateRequest(adminUserId, jobId))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw new AuthAdminKeyException("Failed to activate Admin Key", exception);
    }
  }

  public void deactivate(String adminUserId, String jobId) {
    try {
      authServerRestClient
          .post()
          .uri("/internal/admin/key/deactivate")
          .contentType(MediaType.APPLICATION_JSON)
          .header(INTERNAL_API_KEY_HEADER, internalApiKey == null ? "" : internalApiKey)
          .body(new AdminKeyDeactivateRequest(adminUserId, jobId))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw new AuthAdminKeyException("Failed to deactivate Admin Key", exception);
    }
  }

  public static class AuthAdminKeyException extends BizException {

    public AuthAdminKeyException(String message, Throwable cause) {
      super(ErrorCode.EXTERNAL_SERVICE_ERROR, message, cause);
    }
  }
}
