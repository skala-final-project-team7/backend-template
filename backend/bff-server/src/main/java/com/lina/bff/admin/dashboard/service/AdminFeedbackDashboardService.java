package com.lina.bff.admin.dashboard.service;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackResponse;
import com.lina.bff.admin.dashboard.dto.AdminFeedbackTrendItemResponse;
import com.lina.bff.admin.dashboard.dto.NegativeFeedbackResponse;
import com.lina.bff.admin.dashboard.repository.AdminFeedbackRepository;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.feedback.entity.Feedback;
import com.lina.bff.feedback.entity.FeedbackRating;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 피드백 현황 집계 서비스.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 6 — 긍정/부정 집계, KST 추이, 부정 피드백 QCA 매핑 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class AdminFeedbackDashboardService {

  private static final DateTimeFormatter HOURLY_BUCKET_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");

  private final AdminFeedbackRepository adminFeedbackRepository;

  public AdminFeedbackResponse getFeedback(AdminDashboardQuery query) {
    Instant from = query.timeRange().fromUtc();
    Instant to = query.timeRange().toUtc();
    List<Feedback> feedbacks = adminFeedbackRepository.findFeedbacksBetween(from, to);
    long likeCount = countByRating(feedbacks, FeedbackRating.LIKE);
    long dislikeCount = countByRating(feedbacks, FeedbackRating.DISLIKE);
    long totalCount = likeCount + dislikeCount;

    return new AdminFeedbackResponse(
        totalCount,
        likeCount,
        dislikeCount,
        positiveRatio(likeCount, totalCount),
        trend(feedbacks, query.period()),
        negativeFeedbacks(from, to, query),
        query.pageRequest().page(),
        query.pageRequest().size());
  }

  private long countByRating(List<Feedback> feedbacks, FeedbackRating rating) {
    return feedbacks.stream().filter(feedback -> rating.equals(feedback.getRating())).count();
  }

  private double positiveRatio(long likeCount, long totalCount) {
    if (totalCount == 0) {
      return 0.0;
    }
    return Math.round(((double) likeCount / totalCount) * 10_000.0) / 10_000.0;
  }

  private List<AdminFeedbackTrendItemResponse> trend(
      List<Feedback> feedbacks, AdminDashboardPeriod period) {
    Map<String, RatingBucket> buckets = new TreeMap<>();
    for (Feedback feedback : feedbacks) {
      if (feedback.getCreatedAt() == null || feedback.getRating() == null) {
        continue;
      }

      String bucketKey = bucketKey(feedback.getCreatedAt(), period);
      buckets
          .computeIfAbsent(bucketKey, ignored -> new RatingBucket())
          .increment(feedback.getRating());
    }

    return buckets.entrySet().stream()
        .map(
            entry ->
                new AdminFeedbackTrendItemResponse(
                    entry.getKey(), entry.getValue().likeCount, entry.getValue().dislikeCount))
        .toList();
  }

  private String bucketKey(Instant createdAt, AdminDashboardPeriod period) {
    if (AdminDashboardPeriod.HOURLY.equals(period)) {
      return createdAt.atZone(AdminDashboardQueryParser.KST).format(HOURLY_BUCKET_FORMATTER);
    }
    return createdAt.atZone(AdminDashboardQueryParser.KST).toLocalDate().toString();
  }

  private List<NegativeFeedbackResponse> negativeFeedbacks(
      Instant from, Instant to, AdminDashboardQuery query) {
    List<Feedback> negativeFeedbacks =
        adminFeedbackRepository.findNegativeFeedbacksBetween(from, to, query.pageRequest());
    Map<String, Message> assistantMessages =
        adminFeedbackRepository.findActiveMessagesByIds(messageIds(negativeFeedbacks));
    Map<String, String> questionsByAssistantId =
        questionsByAssistantId(assistantMessages.values(), assistantMessages.keySet());

    return negativeFeedbacks.stream()
        .map(
            feedback ->
                toNegativeFeedbackResponse(feedback, assistantMessages, questionsByAssistantId))
        .filter(Objects::nonNull)
        .toList();
  }

  private Set<String> messageIds(List<Feedback> feedbacks) {
    return feedbacks.stream()
        .map(Feedback::getMessageId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private Map<String, String> questionsByAssistantId(
      Collection<Message> assistantMessages, Set<String> targetAssistantIds) {
    Set<String> conversationIds =
        assistantMessages.stream()
            .map(Message::getConversationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<Message> conversationMessages =
        adminFeedbackRepository.findActiveMessagesByConversationIds(conversationIds);

    Map<String, String> questionsByAssistantId = new HashMap<>();
    Map<String, String> lastUserContentByConversationId = new LinkedHashMap<>();
    for (Message message : conversationMessages) {
      if (message.getConversationId() == null || message.getRole() == null) {
        continue;
      }

      if (MessageRole.user.equals(message.getRole())) {
        lastUserContentByConversationId.put(message.getConversationId(), message.getContent());
        continue;
      }

      if (MessageRole.assistant.equals(message.getRole())
          && targetAssistantIds.contains(message.getMessageId())) {
        questionsByAssistantId.put(
            message.getMessageId(),
            lastUserContentByConversationId.get(message.getConversationId()));
      }
    }
    return questionsByAssistantId;
  }

  private NegativeFeedbackResponse toNegativeFeedbackResponse(
      Feedback feedback,
      Map<String, Message> assistantMessages,
      Map<String, String> questionsByAssistantId) {
    Message assistantMessage = assistantMessages.get(feedback.getMessageId());
    if (assistantMessage == null || !MessageRole.assistant.equals(assistantMessage.getRole())) {
      return null;
    }

    return new NegativeFeedbackResponse(
        feedback.getFeedbackId(),
        feedback.getMessageId(),
        feedback.getComment(),
        questionsByAssistantId.get(feedback.getMessageId()),
        assistantMessage.getContent(),
        feedback.getCreatedAt() == null
            ? null
            : feedback.getCreatedAt().atZone(AdminDashboardQueryParser.KST));
  }

  private static final class RatingBucket {
    private long likeCount;
    private long dislikeCount;

    private void increment(FeedbackRating rating) {
      if (FeedbackRating.LIKE.equals(rating)) {
        likeCount++;
      }
      if (FeedbackRating.DISLIKE.equals(rating)) {
        dislikeCount++;
      }
    }
  }
}
