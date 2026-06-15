package com.lina.bff.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import com.lina.bff.admin.dashboard.dto.AdminDashboardPeriod;
import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminDashboardTimeRange;
import com.lina.bff.admin.dashboard.dto.AdminUsersResponse;
import com.lina.bff.admin.dashboard.repository.AdminUserMongoRepository;
import com.lina.bff.admin.dashboard.repository.AdminUserPage;
import com.lina.bff.admin.dashboard.repository.AdminUserReadRepository;
import com.lina.bff.admin.dashboard.repository.AdminUserRow;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUsersServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Mock private AdminUserReadRepository adminUserReadRepository;
  @Mock private AdminUserMongoRepository adminUserMongoRepository;

  @Test
  @DisplayName("사용자 페이지, 활성 사용자 수, 사용자별 대화 수를 조합해 반환한다")
  void shouldReturnUsersWithConversationCounts() {
    AdminDashboardQuery query =
        query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00", 1, 2);
    AdminDashboardPageRequest pageRequest = query.pageRequest();
    List<AdminUserRow> users =
        List.of(
            user("712020:admin", "admin@example.com", "관리자", "ADMIN", "2026-06-10T01:30:00Z"),
            user("712020:user", "user@example.com", "사용자", "USER", "2026-06-09T15:00:00Z"));
    when(adminUserReadRepository.findUsers(
            pageRequest,
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(new AdminUserPage(10L, 2L, users));
    when(adminUserMongoRepository.countActiveConversationsByUserIds(
            List.of("712020:admin", "712020:user")))
        .thenReturn(Map.of("712020:admin", 3L, "712020:user", 1L));

    AdminUsersResponse response =
        new AdminUsersService(adminUserReadRepository, adminUserMongoRepository).getUsers(query);

    assertThat(response.totalUsers()).isEqualTo(10);
    assertThat(response.dailyActiveUsers()).isEqualTo(2);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(2);
    assertThat(response.users()).hasSize(2);
    assertThat(response.users().getFirst().userId()).isEqualTo("712020:admin");
    assertThat(response.users().getFirst().lastAccessAt().toOffsetDateTime().toString())
        .isEqualTo("2026-06-10T10:30+09:00");
    assertThat(response.users().getFirst().conversationCount()).isEqualTo(3);
    assertThat(response.users().getFirst().accessibleSpaceCount()).isZero();
    assertThat(response.users().getFirst().accessiblePageCount()).isZero();
    assertThat(response.users().getFirst().accessibleAttachmentCount()).isZero();
  }

  @Test
  @DisplayName("사용자가 없으면 빈 목록과 0 집계를 반환한다")
  void shouldReturnEmptyUsersWhenNoData() {
    AdminDashboardQuery query =
        query("2026-06-10T00:00:00+09:00", "2026-06-11T00:00:00+09:00", 0, 20);
    when(adminUserReadRepository.findUsers(
            query.pageRequest(),
            Instant.parse("2026-06-09T15:00:00Z"),
            Instant.parse("2026-06-10T15:00:00Z")))
        .thenReturn(new AdminUserPage(0L, 0L, List.of()));
    when(adminUserMongoRepository.countActiveConversationsByUserIds(List.of()))
        .thenReturn(Map.of());

    AdminUsersResponse response =
        new AdminUsersService(adminUserReadRepository, adminUserMongoRepository).getUsers(query);

    assertThat(response.totalUsers()).isZero();
    assertThat(response.dailyActiveUsers()).isZero();
    assertThat(response.users()).isEmpty();
  }

  private static AdminDashboardQuery query(String fromKst, String toKst, int page, int size) {
    ZonedDateTime from = ZonedDateTime.parse(fromKst).withZoneSameInstant(KST);
    ZonedDateTime to = ZonedDateTime.parse(toKst).withZoneSameInstant(KST);
    return new AdminDashboardQuery(
        AdminDashboardPeriod.DAILY,
        new AdminDashboardTimeRange(from, to, from.toInstant(), to.toInstant()),
        new AdminDashboardPageRequest(page, size));
  }

  private static AdminUserRow user(
      String userId, String email, String name, String role, String lastLoginAt) {
    return new AdminUserRow(
        UUID.randomUUID(),
        userId,
        email,
        name,
        "https://example.com/avatar.png",
        role,
        Instant.parse(lastLoginAt),
        2L);
  }
}
