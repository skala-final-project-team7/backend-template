package com.lina.auth.token;

import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSeedService {

  /** LINA 세션 JWT placeholder — seed 는 로그인 전이라 미발급, 첫 로그인 시 실제 access JWT 로 덮어쓴다. */
  private static final String ACCESS_TOKEN_PLACEHOLDER = "ADMIN_PLACEHOLDER";

  private static final Logger log = LoggerFactory.getLogger(AdminSeedService.class);

  private final AdminSeedProperties properties;
  private final UserRepository userRepository;
  private final AdminAtlassianCredentialRepository adminCredentialRepository;

  /** 멱등 seed. 토큰 원문은 로그에 남기지 않는다(식별자만). */
  @Transactional
  public void seed() {
    if (!properties.isConfigured()) {
      log.info("admin seed 미설정 — 건너뜀(lina.admin-seed.* env 미주입)");
      return;
    }

    User admin =
        userRepository
            .findByUserId(properties.getAccountId())
            .orElseGet(() -> userRepository.save(buildAdminUser()));

    if (adminCredentialRepository.findById(admin.getUserKey()).isEmpty()) {
      adminCredentialRepository.save(buildCredential(admin.getUserKey()));
      log.info("admin_atlassian_credential seed 완료 — userId={}", admin.getUserId());
    }
  }

  private User buildAdminUser() {
    return User.builder()
        .userId(properties.getAccountId())
        .email(properties.getEmail())
        .name(properties.getName())
        .role(UserRole.ADMIN)
        .accessToken(ACCESS_TOKEN_PLACEHOLDER)
        .build();
  }

  private AdminAtlassianCredential buildCredential(UUID userKey) {
    return AdminAtlassianCredential.builder()
        .userKey(userKey)
        .siteUrl(properties.getSiteUrl())
        .adminApiToken(properties.getApiToken())
        .build();
  }
}
