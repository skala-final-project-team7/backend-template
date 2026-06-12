package com.lina.bff.admin.dashboard.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminDashboardQueryParserTest {

  private final AdminDashboardQueryParser parser =
      new AdminDashboardQueryParser(
          Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC));

  @Test
  @DisplayName("파라미터를 생략하면 daily, 최근 7일, page=0, size=20 기본값을 적용한다")
  void shouldApplyDefaults() {
    AdminDashboardQuery query = parser.parse(null, null, null, null, null);

    assertThat(query.period()).isEqualTo(AdminDashboardPeriod.DAILY);
    assertThat(query.pageRequest().page()).isZero();
    assertThat(query.pageRequest().size()).isEqualTo(20);
    assertThat(query.timeRange().toKst().toOffsetDateTime().toString())
        .isEqualTo("2026-06-12T12:00+09:00");
    assertThat(query.timeRange().fromKst().toOffsetDateTime().toString())
        .isEqualTo("2026-06-05T12:00+09:00");
    assertThat(query.timeRange().toUtc()).isEqualTo(Instant.parse("2026-06-12T03:00:00Z"));
    assertThat(query.timeRange().fromUtc()).isEqualTo(Instant.parse("2026-06-05T03:00:00Z"));
  }

  @Test
  @DisplayName("hourly period 와 명시적 KST 기간을 UTC 조회 범위로 변환한다")
  void shouldParseHourlyPeriodAndExplicitRange() {
    AdminDashboardQuery query =
        parser.parse("hourly", "2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00", 2, 50);

    assertThat(query.period()).isEqualTo(AdminDashboardPeriod.HOURLY);
    assertThat(query.pageRequest().page()).isEqualTo(2);
    assertThat(query.pageRequest().size()).isEqualTo(50);
    assertThat(query.timeRange().fromUtc()).isEqualTo(Instant.parse("2026-06-09T15:00:00Z"));
    assertThat(query.timeRange().toUtc()).isEqualTo(Instant.parse("2026-06-10T15:00:00Z"));
  }

  @Test
  @DisplayName("허용하지 않는 period 는 INVALID_REQUEST 로 거부한다")
  void shouldRejectInvalidPeriod() {
    assertThatThrownBy(() -> parser.parse("weekly", null, null, null, null))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("from 이 to 이상이면 INVALID_REQUEST 로 거부한다")
  void shouldRejectInvalidRangeOrder() {
    assertThatThrownBy(
            () ->
                parser.parse(
                    null, "2026-06-11T00:00:00+09:00", "2026-06-11T00:00:00+09:00", null, null))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("page 는 0 이상이어야 한다")
  void shouldRejectNegativePage() {
    assertThatThrownBy(() -> parser.parse(null, null, null, -1, null))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("size 는 1 이상 100 이하이어야 한다")
  void shouldRejectInvalidSize() {
    assertThatThrownBy(() -> parser.parse(null, null, null, null, 101))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }
}
