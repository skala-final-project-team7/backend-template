package com.lina.common.response;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Backend 공통 실패 응답 Wrapper. 모든 에러 응답은 본 구조로 직렬화한다.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, success/error(code, message) 구조 정의
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x, Jackson 2.17.x
 *   - docs/conventions.md §10 에러 형식 준수
 * --------------------------------------------------
 * </pre>
 */
public record ErrorResponse(boolean success, ErrorBody error) {

  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse(false, new ErrorBody(code, message));
  }

  public record ErrorBody(String code, String message) {}
}
