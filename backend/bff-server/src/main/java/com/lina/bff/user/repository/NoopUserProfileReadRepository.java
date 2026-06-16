package com.lina.bff.user.repository;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** MySQL read datasource 미설정 로컬 환경용 fallback. */
@Repository
@ConditionalOnProperty(
    prefix = "lina.admin.dashboard.mysql",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopUserProfileReadRepository implements UserProfileReadRepository {

  @Override
  public Optional<UserProfileRow> findByUserId(String userId) {
    return Optional.empty();
  }
}
