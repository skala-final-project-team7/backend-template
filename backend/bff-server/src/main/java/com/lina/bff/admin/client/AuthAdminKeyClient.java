package com.lina.bff.admin.client;

import com.lina.bff.admin.client.dto.AdminKeyActivateRequest;
import com.lina.bff.admin.client.dto.AdminKeyDeactivateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class AuthAdminKeyClient {

  private final RestClient authServerRestClient;

  public void activate(String adminUserId, String jobId) {
    try {
      authServerRestClient
          .post()
          .uri("/internal/admin/key/activate")
          .contentType(MediaType.APPLICATION_JSON)
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
          .body(new AdminKeyDeactivateRequest(adminUserId, jobId))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw new AuthAdminKeyException("Failed to deactivate Admin Key", exception);
    }
  }

  public static class AuthAdminKeyException extends RuntimeException {

    public AuthAdminKeyException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
