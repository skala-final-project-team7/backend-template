package com.lina.auth.token.repository;

import com.lina.auth.token.entity.AdminAtlassianCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * admin_atlassian_credential 영속. Admin Key 관리(Feature 6) 시 user_key 로 조회한다(docs/db-schema.md §6.4).
 */
public interface AdminAtlassianCredentialRepository
    extends JpaRepository<AdminAtlassianCredential, UUID> {}
