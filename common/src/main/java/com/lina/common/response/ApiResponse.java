package com.lina.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Backend 공통 성공 응답 Wrapper. 모든 API 응답은 본 구조로 직렬화한다.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, success/data/message 3-field 구조 정의
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x, Jackson 2.17.x
 *   - docs/conventions.md §10 응답 형식 준수
 * --------------------------------------------------
 * </pre>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, String message) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message);
  }
}
