package com.lina.bff.admin.dashboard.service;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse;
import com.lina.bff.admin.dashboard.dto.AdminStatsResponse.HourlyAccessTrendItem;
import com.lina.bff.admin.dashboard.repository.AdminStatsMongoRepository;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 사용 통계 집계 서비스.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 3 — 사용자 질문 수, 평균 응답 시간, 전체 대화 수, 시간대별 추이 집계
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

  private final AdminStatsMongoRepository adminStatsMongoRepository;

  public AdminStatsResponse getStats(AdminDashboardQuery query) {
    List<Message> messages =
        adminStatsMongoRepository.findActiveMessagesBetween(
            query.timeRange().fromUtc(), query.timeRange().toUtc());
    long totalConversations = adminStatsMongoRepository.countActiveConversations();
    List<Message> userMessages = messages.stream().filter(this::isUserMessage).toList();

    return new AdminStatsResponse(
        userMessages.size(),
        averageResponseTimeSeconds(messages),
        totalConversations,
        hourlyAccessTrend(userMessages));
  }

  private List<HourlyAccessTrendItem> hourlyAccessTrend(List<Message> userMessages) {
    Map<Integer, Long> countByHour = new TreeMap<>();
    for (Message message : userMessages) {
      int hour =
          message.getCreatedAt().atZone(AdminDashboardQueryParser.KST).toLocalTime().getHour();
      countByHour.merge(hour, 1L, Long::sum);
    }
    return countByHour.entrySet().stream()
        .map(entry -> new HourlyAccessTrendItem(entry.getKey(), entry.getValue()))
        .toList();
  }

  private double averageResponseTimeSeconds(List<Message> messages) {
    Map<String, Message> pendingUserMessages = new HashMap<>();
    List<Long> responseTimes = new ArrayList<>();

    messages.stream()
        .filter(message -> message.getConversationId() != null)
        .filter(message -> message.getCreatedAt() != null)
        .forEach(
            message -> {
              if (isUserMessage(message)) {
                pendingUserMessages.put(message.getConversationId(), message);
                return;
              }

              if (isAssistantMessage(message)) {
                Message userMessage = pendingUserMessages.remove(message.getConversationId());
                if (userMessage != null) {
                  long seconds =
                      Duration.between(userMessage.getCreatedAt(), message.getCreatedAt())
                          .toSeconds();
                  if (seconds >= 0) {
                    responseTimes.add(seconds);
                  }
                }
              }
            });

    if (responseTimes.isEmpty()) {
      return 0.0;
    }

    double average = responseTimes.stream().mapToLong(Long::longValue).average().orElseThrow();
    return Math.round(average * 10.0) / 10.0;
  }

  private boolean isUserMessage(Message message) {
    return MessageRole.user.equals(message.getRole());
  }

  private boolean isAssistantMessage(Message message) {
    return MessageRole.assistant.equals(message.getRole());
  }
}
