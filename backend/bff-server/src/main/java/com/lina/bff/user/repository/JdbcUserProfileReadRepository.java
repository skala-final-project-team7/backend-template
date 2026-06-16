package com.lina.bff.user.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** `adminDashboardJdbcClient` 를 공유해 MySQL `users` 를 read-only 조회한다. */
@Repository
@ConditionalOnBean(name = "adminDashboardJdbcClient")
public class JdbcUserProfileReadRepository implements UserProfileReadRepository {

  private final JdbcClient jdbcClient;

  public JdbcUserProfileReadRepository(
      @Qualifier("adminDashboardJdbcClient") JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public Optional<UserProfileRow> findByUserId(String userId) {
    return jdbcClient
        .sql(
            """
            select user_id,
                   name,
                   email,
                   role,
                   profile_image_url,
                   last_login_at
            from users
            where user_id = :userId
            """)
        .param("userId", userId)
        .query(this::mapUser)
        .optional();
  }

  private UserProfileRow mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
    Timestamp lastLoginAt = resultSet.getTimestamp("last_login_at");
    return new UserProfileRow(
        resultSet.getString("user_id"),
        resultSet.getString("name"),
        resultSet.getString("email"),
        resultSet.getString("role"),
        resultSet.getString("profile_image_url"),
        lastLoginAt == null ? null : lastLoginAt.toInstant());
  }
}
