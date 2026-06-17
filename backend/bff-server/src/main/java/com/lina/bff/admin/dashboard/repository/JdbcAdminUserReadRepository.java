package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : auth-server MySQL users/user_groups 읽기 전용 JDBC repository.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 4 — 사용자 현황 MySQL 조회 구현
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Spring JDBC
 *   - MySQL 8.x
 * --------------------------------------------------
 * </pre>
 */
@Repository
@ConditionalOnBean(name = "adminDashboardJdbcClient")
public class JdbcAdminUserReadRepository implements AdminUserReadRepository {

  private final JdbcClient jdbcClient;

  public JdbcAdminUserReadRepository(@Qualifier("adminDashboardJdbcClient") JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public AdminUserPage findUsers(
      AdminDashboardPageRequest pageRequest, Instant fromUtc, Instant toUtc) {
    long totalUsers = jdbcClient.sql("select count(*) from users").query(Long.class).single();
    long activeUsers =
        jdbcClient
            .sql(
                """
                select count(*)
                from users
                where last_login_at >= :fromUtc
                  and last_login_at < :toUtc
                """)
            .param("fromUtc", Timestamp.from(fromUtc))
            .param("toUtc", Timestamp.from(toUtc))
            .query(Long.class)
            .single();

    List<AdminUserRow> users =
        jdbcClient
            .sql(
                """
                select u.user_key,
                       u.user_id,
                       u.email,
                       u.name,
                       u.profile_image_url,
                       u.role,
                       u.last_login_at,
                       group_concat(distinct ug.group_id) as group_ids
                from users u
                left join user_groups ug on ug.user_key = u.user_key
                group by u.user_key, u.user_id, u.email, u.name, u.profile_image_url,
                         u.role, u.last_login_at
                order by u.last_login_at is null, u.last_login_at desc, u.user_id asc
                limit :limit offset :offset
                """)
            .param("limit", pageRequest.size())
            .param("offset", (long) pageRequest.page() * pageRequest.size())
            .query(this::mapUser)
            .list();

    return new AdminUserPage(totalUsers, activeUsers, users);
  }

  private AdminUserRow mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
    Timestamp lastLoginAt = resultSet.getTimestamp("last_login_at");
    return new AdminUserRow(
        uuidFromBytes(resultSet.getBytes("user_key")),
        resultSet.getString("user_id"),
        resultSet.getString("email"),
        resultSet.getString("name"),
        resultSet.getString("profile_image_url"),
        resultSet.getString("role"),
        lastLoginAt == null ? null : lastLoginAt.toInstant(),
        parseGroupIds(resultSet.getString("group_ids")));
  }

  /** group_concat 결과("g1,g2,...") 를 리스트로. 그룹 없는 사용자는 null → 빈 리스트. */
  private static List<String> parseGroupIds(String concatenated) {
    if (concatenated == null || concatenated.isBlank()) {
      return List.of();
    }
    return Arrays.stream(concatenated.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }

  private static UUID uuidFromBytes(byte[] bytes) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
  }
}
