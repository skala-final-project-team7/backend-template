package com.lina.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Backend 공통 실패 응답 Wrapper. 모든 에러 응답은 본 구조로 직렬화한다.
 *           성공 응답(ApiResponse)과 동일한 isSuccess/code/message 봉투를 공유하고,
 *           프론트가 분기에 사용하는 도메인 에러 코드는 errorCode로 보존한다.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, success/error(code, message) 구조 정의
 *   - 2026-05-18, api-spec Common Response Wrapper 정합, ApiResponse와 isSuccess/code/message 통일
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x, Jackson 2.17.x
 *   - docs/api-spec.md Common Response Wrapper 형식 준수
 * --------------------------------------------------
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorResponse(boolean isSuccess, int code, String errorCode, String message) {

  public static ErrorResponse of(HttpStatus status, String errorCode, String message) {
    return new ErrorResponse(false, status.value(), errorCode, message);
  }
}
