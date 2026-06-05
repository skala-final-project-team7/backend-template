package com.lina.common.exception;

import com.lina.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Backend 공통 예외 처리. 모든 예외를 ErrorResponse 형식으로 매핑한다.
 *           내부 스택트레이스 및 구현 정보는 사용자 응답에 포함하지 않는다 (docs/conventions.md §6.3).
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, BizException/Validation/Security/Fallback 핸들러 등록
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x, Spring Security 6.3.x
 * --------------------------------------------------
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ErrorResponse> handleBizException(BizException ex) {
    ErrorCode errorCode = ex.getErrorCode();
    log.warn("BizException: code={}, message={}", errorCode.getCode(), ex.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode.getHttpStatus(), errorCode.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(GlobalExceptionHandler::formatFieldError)
            .orElse(ErrorCode.INVALID_REQUEST.getDefaultMessage());
    log.warn("Validation failed: {}", message);
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.INVALID_REQUEST.getHttpStatus(),
                ErrorCode.INVALID_REQUEST.getCode(),
                message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    log.warn("ConstraintViolation: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.INVALID_REQUEST.getHttpStatus(),
                ErrorCode.INVALID_REQUEST.getCode(),
                ex.getMessage()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> handleInvalidRequestFormat(Exception ex) {
    log.warn("Invalid request format: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.INVALID_REQUEST.getHttpStatus(),
                ErrorCode.INVALID_REQUEST.getCode(),
                ErrorCode.INVALID_REQUEST.getDefaultMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    log.warn("AccessDenied: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.FORBIDDEN.getHttpStatus(),
                ErrorCode.FORBIDDEN.getCode(),
                ErrorCode.FORBIDDEN.getDefaultMessage()));
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
    log.warn("AuthenticationException: {}", ex.getMessage());
    return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.UNAUTHORIZED.getHttpStatus(),
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getDefaultMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
        .body(
            ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR.getHttpStatus(),
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
  }

  private static String formatFieldError(FieldError fieldError) {
    String defaultMessage = fieldError.getDefaultMessage();
    return defaultMessage == null
        ? fieldError.getField() + " is invalid"
        : fieldError.getField() + ": " + defaultMessage;
  }
}
