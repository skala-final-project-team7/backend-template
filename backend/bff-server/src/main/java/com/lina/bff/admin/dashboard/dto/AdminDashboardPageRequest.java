package com.lina.bff.admin.dashboard.dto;

import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 목록 API 공통 페이지네이션 파라미터.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 2 — page/size 검증 공통화
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record AdminDashboardPageRequest(int page, int size) {

  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public AdminDashboardPageRequest {
    if (page < 0) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "page는 0 이상이어야 합니다.");
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "size는 1 이상 100 이하이어야 합니다.");
    }
  }

  public static AdminDashboardPageRequest of(Integer page, Integer size) {
    return new AdminDashboardPageRequest(
        page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
  }
}
