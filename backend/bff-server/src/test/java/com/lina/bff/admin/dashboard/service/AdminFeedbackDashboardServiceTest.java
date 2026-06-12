package com.lina.bff.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminDashboardTimeRange;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackResponse;
import com.lina.bff.admin.dashboard.repository.AdminFeedbackRepository;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.feedback.entity.Feedback;
import com.lina.bff.feedback.entity.FeedbackRating;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackDashboardServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock private AdminFeedbackRepository adminFeedbackRepository;

  @Test
  @DisplayName("LIKE/DISLIKE 집계, KST daily 추이, 부정 피드백 QCA 원문을 반환한다")
  void shouldAggregateDailyFeedbackAndMapNegativeQca() {
    AdminDashboardQuery query =
        query(
            AdminDashboardPeriod.DAILY,
            "2026-06-10T00:00:00+09:00",
            "2026-06-11T00:00:00+09:00",
            0,
            20);
    Feedback like1 =
        feedback("fb-1", "a-like-1", FeedbackRating.LIKE, null, "2026-06-09T15:10:00Z");
    Feedback like2 =
        feedback("fb-2", "a-like-2", FeedbackRating.LIKE, null, "2026-06-09T15:20:00Z");
    Feedback dislike =
        feedback("fb-3", "a-1", FeedbackRating.DISLIKE, "출처가 이상합니다.", "2026-06-10T03:00:00Z");
    Message user =
        message("u-1", "conv-1", MessageRole.user, "S3 권한 오류 원인이 뭐야?", "2026-06-10T02:59:00Z");
    Message assistant =
        message("a-1", "conv-1", MessageRole.assistant, "IAM 정책을 확인하세요.", "2026-06-10T03:00:00Z");

    when(adminFeedbackRepository.findFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(List.of(like1, like2, dislike));
    when(adminFeedbackRepository.findNegativeFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of(dislike));
    when(adminFeedbackRepository.findActiveMessagesByIds(Set.of("a-1")))
        .thenReturn(Map.of("a-1", assistant));
    when(adminFeedbackRepository.findActiveMessagesByConversationIds(Set.of("conv-1")))
        .thenReturn(List.of(user, assistant));

    AdminFeedbackResponse response =
        new AdminFeedbackDashboardService(adminFeedbackRepository).getFeedback(query);

    assertThat(response.totalCount()).isEqualTo(3);
    assertThat(response.likeCount()).isEqualTo(2);
    assertThat(response.dislikeCount()).isEqualTo(1);
    assertThat(response.positiveRatio()).isEqualTo(0.6667);
    assertThat(response.trend())
        .extracting("date", "likeCount", "dislikeCount")
        .containsExactly(tuple("2026-06-10", 2L, 1L));
    assertThat(response.negativeFeedbacks())
        .extracting("feedbackId", "messageId", "comment", "question", "answer")
        .containsExactly(tuple("fb-3", "a-1", "출처가 이상합니다.", "S3 권한 오류 원인이 뭐야?", "IAM 정책을 확인하세요."));
    assertThat(response.negativeFeedbacks().get(0).createdAt().toOffsetDateTime().toString())
        .isEqualTo("2026-06-10T12:00+09:00");
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("period=hourly 는 KST 시간 버킷 문자열로 LIKE/DISLIKE 추이를 집계한다")
  void shouldAggregateHourlyTrend() {
    AdminDashboardQuery query =
        query(
            AdminDashboardPeriod.HOURLY,
            "2026-06-10T00:00:00+09:00",
            "2026-06-11T00:00:00+09:00",
            1,
            10);
    when(adminFeedbackRepository.findFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(
            List.of(
                feedback("fb-1", "a-1", FeedbackRating.LIKE, null, "2026-06-09T15:10:00Z"),
                feedback("fb-2", "a-2", FeedbackRating.DISLIKE, null, "2026-06-09T15:20:00Z"),
                feedback("fb-3", "a-3", FeedbackRating.LIKE, null, "2026-06-10T04:00:00Z")));
    when(adminFeedbackRepository.findNegativeFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of());

    AdminFeedbackResponse response =
        new AdminFeedbackDashboardService(adminFeedbackRepository).getFeedback(query);

    assertThat(response.trend())
        .extracting("date", "likeCount", "dislikeCount")
        .containsExactly(tuple("2026-06-10T00:00", 1L, 1L), tuple("2026-06-10T13:00", 1L, 0L));
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(10);
  }

  @Test
  @DisplayName("피드백이 0건이면 positiveRatio=0.0 과 빈 배열을 반환한다")
  void shouldReturnZeroWhenNoFeedback() {
    AdminDashboardQuery query =
        query(
            AdminDashboardPeriod.DAILY,
            "2026-06-10T00:00:00+09:00",
            "2026-06-11T00:00:00+09:00",
            0,
            20);
    when(adminFeedbackRepository.findFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(List.of());
    when(adminFeedbackRepository.findNegativeFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of());

    AdminFeedbackResponse response =
        new AdminFeedbackDashboardService(adminFeedbackRepository).getFeedback(query);

    assertThat(response.totalCount()).isZero();
    assertThat(response.likeCount()).isZero();
    assertThat(response.dislikeCount()).isZero();
    assertThat(response.positiveRatio()).isZero();
    assertThat(response.trend()).isEmpty();
    assertThat(response.negativeFeedbacks()).isEmpty();
  }

  @Test
  @DisplayName("부정 피드백 대상 assistant 메시지가 삭제/누락되면 QCA 목록에서 제외한다")
  void shouldExcludeNegativeFeedbackWhenAssistantMessageMissing() {
    AdminDashboardQuery query =
        query(
            AdminDashboardPeriod.DAILY,
            "2026-06-10T00:00:00+09:00",
            "2026-06-11T00:00:00+09:00",
            0,
            20);
    Feedback dislike =
        feedback(
            "fb-1",
            "missing-assistant",
            FeedbackRating.DISLIKE,
            "응답이 없습니다.",
            "2026-06-10T03:00:00Z");
    when(adminFeedbackRepository.findFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(List.of(dislike));
    when(adminFeedbackRepository.findNegativeFeedbacksBetween(
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z"),
            query.pageRequest()))
        .thenReturn(List.of(dislike));
    when(adminFeedbackRepository.findActiveMessagesByIds(Set.of("missing-assistant")))
        .thenReturn(Map.of());

    AdminFeedbackResponse response =
        new AdminFeedbackDashboardService(adminFeedbackRepository).getFeedback(query);

    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.negativeFeedbacks()).isEmpty();
    verify(adminFeedbackRepository).findActiveMessagesByConversationIds(Set.of());
  }

  private static AdminDashboardQuery query(
      AdminDashboardPeriod period, String fromKst, String toKst, int page, int size) {
    ZonedDateTime from = ZonedDateTime.parse(fromKst).withZoneSameInstant(KST);
    ZonedDateTime to = ZonedDateTime.parse(toKst).withZoneSameInstant(KST);
    return new AdminDashboardQuery(
        period,
        new AdminDashboardTimeRange(from, to, from.toInstant(), to.toInstant()),
        new AdminDashboardPageRequest(page, size));
  }

  private static Feedback feedback(
      String feedbackId,
      String messageId,
      FeedbackRating rating,
      String comment,
      String createdAt) {
    return Feedback.builder()
        .feedbackId(feedbackId)
        .messageId(messageId)
        .rating(rating)
        .comment(comment)
        .createdAt(Instant.parse(createdAt))
        .build();
  }

  private static Message message(
      String messageId, String conversationId, MessageRole role, String content, String createdAt) {
    return Message.builder()
        .messageId(messageId)
        .conversationId(conversationId)
        .role(role)
        .content(content)
        .createdAt(Instant.parse(createdAt))
        .build();
  }
}
