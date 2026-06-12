package com.lina.auth.oauth;

import com.lina.common.exception.ErrorCode;
import com.lina.common.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Auth API 전용 예외 포맷 매핑.
 * --------------------------------------------------
 * </pre>
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex) {
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.INVALID_REQUEST.getHttpStatus(),
                ErrorCode.INVALID_REQUEST.getCode(),
                ErrorCode.INVALID_REQUEST.getDefaultMessage()));
  }
}
