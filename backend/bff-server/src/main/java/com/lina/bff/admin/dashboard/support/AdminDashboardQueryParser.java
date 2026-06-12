package com.lina.bff.admin.dashboard.support;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminDashboardTimeRange;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 공통 query parameter 파서.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 2 — period/from/to/page/size 검증 및 KST 기간 기본값 처리
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
public class AdminDashboardQueryParser {

  public static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int DEFAULT_LOOKBACK_DAYS = 7;

  private final Clock clock;

  public AdminDashboardQueryParser(Clock clock) {
    this.clock = clock;
  }

  public AdminDashboardQuery parse(
      String period, String from, String to, Integer page, Integer size) {
    return new AdminDashboardQuery(
        AdminDashboardPeriod.from(period),
        parseTimeRange(from, to),
        AdminDashboardPageRequest.of(page, size));
  }

  public AdminDashboardTimeRange parseTimeRange(String from, String to) {
    ZonedDateTime nowKst = ZonedDateTime.now(clock).withZoneSameInstant(KST);
    ZonedDateTime toKst = hasText(to) ? parseKstDateTime(to, "to") : nowKst;
    ZonedDateTime fromKst =
        hasText(from) ? parseKstDateTime(from, "from") : toKst.minusDays(DEFAULT_LOOKBACK_DAYS);

    if (!fromKst.isBefore(toKst)) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "from은 to보다 이전이어야 합니다.");
    }

    return new AdminDashboardTimeRange(fromKst, toKst, fromKst.toInstant(), toKst.toInstant());
  }

  private static ZonedDateTime parseKstDateTime(String value, String parameterName) {
    try {
      return ZonedDateTime.parse(value.trim()).withZoneSameInstant(KST);
    } catch (DateTimeParseException ex) {
      throw new BizException(
          ErrorCode.INVALID_REQUEST, parameterName + "은 ISO-8601 timestamp 여야 합니다.", ex);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
