package com.lina.bff.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminDashboardTimeRange;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse;
import com.lina.bff.admin.dashboard.repository.AdminStatsMongoRepository;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock private AdminStatsMongoRepository adminStatsMongoRepository;

  @Test
  @DisplayName("지정 기간 사용자 질문 수, KST 시간대별 추이, 평균 응답 시간을 집계한다")
  void shouldAggregateStatsFromMessages() {
    AdminDashboardQuery query = query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00");
    when(adminStatsMongoRepository.findActiveMessagesBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(
            List.of(
                message("m1", "conv-1", MessageRole.user, "2026-06-09T15:10:00Z"),
                message("m2", "conv-1", MessageRole.assistant, "2026-06-09T15:10:04Z"),
                message("m3", "conv-2", MessageRole.user, "2026-06-10T04:30:00Z"),
                message("m4", "conv-2", MessageRole.assistant, "2026-06-10T04:30:10Z")));
    when(adminStatsMongoRepository.countActiveMessages()).thenReturn(7L);

    AdminStatsResponse response = new AdminStatsService(adminStatsMongoRepository).getStats(query);

    assertThat(response.dailyQueryCount()).isEqualTo(2);
    assertThat(response.avgResponseTime()).isEqualTo(7.0);
    assertThat(response.totalConversations()).isEqualTo(7);
    assertThat(response.hourlyAccessTrend())
        .extracting("hour", "count")
        .containsExactly(tuple(0, 1L), tuple(13, 1L));
  }

  @Test
  @DisplayName("KST 날짜 경계에 걸친 메시지는 KST hour 기준으로 집계한다")
  void shouldBucketHourlyTrendByKstBoundary() {
    AdminDashboardQuery query = query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00");
    when(adminStatsMongoRepository.findActiveMessagesBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(
            List.of(
                message("m1", "conv-1", MessageRole.user, "2026-06-09T15:00:00Z"),
                message("m2", "conv-2", MessageRole.user, "2026-06-10T14:59:59Z")));

    AdminStatsResponse response = new AdminStatsService(adminStatsMongoRepository).getStats(query);

    assertThat(response.hourlyAccessTrend())
        .extracting("hour", "count")
        .containsExactly(tuple(0, 1L), tuple(23, 1L));
  }

  @Test
  @DisplayName("데이터가 없으면 0 집계와 빈 시간대 배열을 반환한다")
  void shouldReturnZeroStatsWhenNoData() {
    AdminDashboardQuery query = query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00");
    when(adminStatsMongoRepository.findActiveMessagesBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(List.of());
    when(adminStatsMongoRepository.countActiveMessages()).thenReturn(0L);

    AdminStatsResponse response = new AdminStatsService(adminStatsMongoRepository).getStats(query);

    assertThat(response.dailyQueryCount()).isZero();
    assertThat(response.avgResponseTime()).isZero();
    assertThat(response.totalConversations()).isZero();
    assertThat(response.hourlyAccessTrend()).isEmpty();
  }

  private static AdminDashboardQuery query(String fromKst, String toKst) {
    ZonedDateTime from = ZonedDateTime.parse(fromKst).withZoneSameInstant(KST);
    ZonedDateTime to = ZonedDateTime.parse(toKst).withZoneSameInstant(KST);
    return new AdminDashboardQuery(
        AdminDashboardPeriod.DAILY,
        new AdminDashboardTimeRange(from, to, from.toInstant(), to.toInstant()),
        AdminDashboardPageRequest.of(null, null));
  }

  private static Message message(
      String messageId, String conversationId, MessageRole role, String createdAt) {
    return Message.builder()
        .messageId(messageId)
        .conversationId(conversationId)
        .role(role)
        .content(role.name())
        .createdAt(Instant.parse(createdAt))
        .build();
  }
}
