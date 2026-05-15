package com.lina.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleBizException_should_map_to_error_code_status() {
    BizException ex = new BizException(ErrorCode.RESOURCE_NOT_FOUND, "대화를 찾을 수 없습니다.");

    ResponseEntity<ErrorResponse> response = handler.handleBizException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().success()).isFalse();
    assertThat(response.getBody().error().code()).isEqualTo("RESOURCE_NOT_FOUND");
    assertThat(response.getBody().error().message()).isEqualTo("대화를 찾을 수 없습니다.");
  }

  @Test
  void handleConstraintViolation_should_return_400() {
    ConstraintViolationException ex = new ConstraintViolationException("invalid", Set.of());

    ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void handleAccessDenied_should_return_403_without_leaking_message() {
    AccessDeniedException ex = new AccessDeniedException("internal detail");

    ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("FORBIDDEN");
    assertThat(response.getBody().error().message()).doesNotContain("internal detail");
  }

  @Test
  void handleAuthentication_should_return_401_without_leaking_message() {
    AuthenticationException ex =
        new AuthenticationException("token signature invalid") {
          private static final long serialVersionUID = 1L;
        };

    ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("UNAUTHORIZED");
    assertThat(response.getBody().error().message()).doesNotContain("token signature");
  }

  @Test
  void handleUnexpected_should_return_500_with_generic_message() {
    Exception ex = new IllegalStateException("DB connection refused");

    ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().error().message()).doesNotContain("DB connection refused");
  }
}
