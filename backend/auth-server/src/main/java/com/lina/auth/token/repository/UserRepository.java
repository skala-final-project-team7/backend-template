package com.lina.auth.token.repository;

import com.lina.auth.token.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** users 영속. OAuth callback 의 accountId lookup → upsert / role 조회에 사용한다(docs/db-schema.md §6.1). */
public interface UserRepository extends JpaRepository<User, UUID> {

  /** Confluence accountId(user_id) 로 단건 조회. 행 없으면 신규 INSERT(role=USER) 대상이다. */
  Optional<User> findByUserId(String userId);
}
