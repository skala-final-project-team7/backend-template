package com.lina.auth.token.repository;

import com.lina.auth.token.entity.UserToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * user_tokens 영속. 조회/저장은 user_key(FK=PK) 기준이며 refresh 회전 시 동일 레코드를 덮어쓴다(docs/db-schema.md §6.2).
 */
public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {}
