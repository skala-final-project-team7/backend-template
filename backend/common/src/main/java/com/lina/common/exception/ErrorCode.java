package com.lina.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Backend 공통 에러 코드 정의. HTTP Status / API code / 기본 메시지를 enum 단위로 묶는다.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, backend/CLAUDE.md §4 매트릭스 기반 6개 기본 코드 정의
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x (HttpStatus 사용)
 * --------------------------------------------------
 * </pre>
 */
@Getter
public enum ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 유효하지 않습니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
  EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR", "외부 서비스 호출에 실패했습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "내부 서버 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String defaultMessage;

  ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
    this.httpStatus = httpStatus;
    this.code = code;
    this.defaultMessage = defaultMessage;
  }
}
