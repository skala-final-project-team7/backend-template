package com.lina.bff.admin.dashboard.dto;

import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.util.Arrays;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 추이 집계 단위 enum.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 2 — daily/hourly 공통 파라미터 파서 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public enum AdminDashboardPeriod {
  DAILY("daily"),
  HOURLY("hourly");

  private final String wireValue;

  AdminDashboardPeriod(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static AdminDashboardPeriod from(String value) {
    if (value == null || value.isBlank()) {
      return DAILY;
    }
    return Arrays.stream(values())
        .filter(period -> period.wireValue.equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(
            () -> new BizException(ErrorCode.INVALID_REQUEST, "period는 daily 또는 hourly 만 허용됩니다."));
  }
}
