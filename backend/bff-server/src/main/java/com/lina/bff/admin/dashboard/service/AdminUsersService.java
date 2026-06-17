package com.lina.bff.admin.dashboard.service;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminUserSummaryResponse;
import com.lina.bff.admin.dashboard.dto.AdminUsersResponse;
import com.lina.bff.admin.dashboard.repository.AdminUserMongoRepository;
import com.lina.bff.admin.dashboard.repository.AdminUserMongoRepository.AccessibleCounts;
import com.lina.bff.admin.dashboard.repository.AdminUserPage;
import com.lina.bff.admin.dashboard.repository.AdminUserReadRepository;
import com.lina.bff.admin.dashboard.repository.AdminUserRow;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 사용자 현황 집계 서비스.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — 사용자 총계, 활성 사용자, 사용자별 대화 수 집계
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class AdminUsersService {

  private final AdminUserReadRepository adminUserReadRepository;
  private final AdminUserMongoRepository adminUserMongoRepository;

  public AdminUsersResponse getUsers(AdminDashboardQuery query) {
    AdminUserPage userPage =
        adminUserReadRepository.findUsers(
            query.pageRequest(), query.timeRange().fromUtc(), query.timeRange().toUtc());
    List<String> userIds = userPage.users().stream().map(AdminUserRow::userId).toList();
    // "대화수" 항목은 채팅방 수가 아니라 메시지 수로 표시한다(응답 필드명은 conversationCount 유지).
    Map<String, Long> conversationCounts =
        adminUserMongoRepository.countActiveMessagesByUserIds(userIds);

    List<AdminUserSummaryResponse> users =
        userPage.users().stream()
            .map(
                user ->
                    toResponse(
                        user,
                        conversationCounts.getOrDefault(user.userId(), 0L),
                        adminUserMongoRepository.countAccessiblePages(
                            user.userId(), user.groupIds())))
            .toList();

    return new AdminUsersResponse(
        userPage.totalUsers(),
        userPage.activeUsers(),
        query.pageRequest().page(),
        query.pageRequest().size(),
        users);
  }

  private AdminUserSummaryResponse toResponse(
      AdminUserRow user, long conversationCount, AccessibleCounts accessible) {
    AccessibleCounts counts = accessible == null ? AccessibleCounts.ZERO : accessible;
    return new AdminUserSummaryResponse(
        user.userId(),
        user.name(),
        user.email(),
        user.role(),
        user.profileImageUrl(),
        toKst(user.lastLoginAt()),
        conversationCount,
        counts.spaceCount(),
        counts.pageCount(),
        counts.attachmentCount());
  }

  private static ZonedDateTime toKst(java.time.Instant instant) {
    return instant == null ? null : instant.atZone(AdminDashboardQueryParser.KST);
  }
}
