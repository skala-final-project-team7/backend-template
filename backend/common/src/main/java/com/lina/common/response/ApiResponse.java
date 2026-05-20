package com.lina.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Backend 공통 성공 응답 Wrapper. 모든 API 성공 응답은 본 구조로 직렬화한다.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, success/data/message 3-field 구조 정의
 *   - 2026-05-18, api-spec Common Response Wrapper 정합, success→isSuccess 변경 및 code(HTTP 상태) 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x, Jackson 2.17.x
 *   - docs/api-spec.md Common Response Wrapper 형식 준수
 * --------------------------------------------------
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean isSuccess, int code, T data, String message) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, HttpStatus.OK.value(), data, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, HttpStatus.OK.value(), data, message);
  }
}
