package com.lina.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * admin seed(로그인 전 사전 주입) 검증. env 설정 시 `users`(role=ADMIN, access_token=ADMIN_PLACEHOLDER) +
 * `admin_atlassian_credential`(site_url + 평문 API Token→저장 시 암호화)을 멱등 INSERT 하고, 미설정·기존행이면 건너뛴다. 토큰
 * 암호화 자체는 JPA converter(TokenCipher) 책임이라 본 단위 테스트는 평문 토큰 전달·save 호출까지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

  private static final String ACCOUNT_ID = "712020:admin";
  private static final String EMAIL = "admin@example.com";
  private static final String NAME = "관리자";
  private static final String SITE_URL = "https://your-site.atlassian.net";
  private static final String API_TOKEN = "admin-api-token";

  @Mock private UserRepository userRepository;
  @Mock private AdminAtlassianCredentialRepository adminCredentialRepository;

  private AdminSeedService seeder(AdminSeedProperties properties) {
    return new AdminSeedService(properties, userRepository, adminCredentialRepository);
  }

  private AdminSeedProperties configured() {
    return new AdminSeedProperties(ACCOUNT_ID, EMAIL, NAME, SITE_URL, API_TOKEN);
  }

  private User existingAdmin() {
    return User.builder()
        .userKey(UUID.randomUUID())
        .userId(ACCOUNT_ID)
        .email(EMAIL)
        .role(UserRole.ADMIN)
        .accessToken("ADMIN_PLACEHOLDER")
        .build();
  }

  @Test
  @DisplayName("admin seed env 미설정이면 아무 것도 INSERT 하지 않는다(skip)")
  void shouldSkipWhenNotConfigured() {
    seeder(new AdminSeedProperties("", "", "", "", "")).seed();

    verify(userRepository, never()).findByUserId(any());
    verify(userRepository, never()).save(any());
    verify(adminCredentialRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "신규: users(role=ADMIN·access_token=ADMIN_PLACEHOLDER) + admin_atlassian_credential(평문 토큰) 을 INSERT")
  void shouldSeedUserAndCredential() {
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());
    given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
    given(adminCredentialRepository.findById(any())).willReturn(Optional.empty());

    seeder(configured()).seed();

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getUserId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
    assertThat(savedUser.getName()).isEqualTo(NAME);
    assertThat(savedUser.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(savedUser.getAccessToken()).isEqualTo("ADMIN_PLACEHOLDER");

    ArgumentCaptor<AdminAtlassianCredential> credCaptor =
        ArgumentCaptor.forClass(AdminAtlassianCredential.class);
    verify(adminCredentialRepository).save(credCaptor.capture());
    AdminAtlassianCredential savedCred = credCaptor.getValue();
    assertThat(savedCred.getUserKey()).isEqualTo(savedUser.getUserKey());
    assertThat(savedCred.getSiteUrl()).isEqualTo(SITE_URL);
    // 평문 토큰을 엔티티에 전달 — 저장 시 TokenCipher converter 가 암호화(Feature 1 검증)
    assertThat(savedCred.getAdminApiToken()).isEqualTo(API_TOKEN);
  }

  @Test
  @DisplayName("기존 admin 사용자가 있으면 users 는 건드리지 않고(role 보존) credential 만 INSERT")
  void shouldKeepExistingUserAndSeedCredential() {
    User existing = existingAdmin();
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(existing));
    given(adminCredentialRepository.findById(existing.getUserKey())).willReturn(Optional.empty());

    seeder(configured()).seed();

    verify(userRepository, never()).save(any());
    ArgumentCaptor<AdminAtlassianCredential> credCaptor =
        ArgumentCaptor.forClass(AdminAtlassianCredential.class);
    verify(adminCredentialRepository).save(credCaptor.capture());
    assertThat(credCaptor.getValue().getUserKey()).isEqualTo(existing.getUserKey());
  }

  @Test
  @DisplayName("users·credential 이 모두 있으면 완전 멱등(INSERT 없음)")
  void shouldBeFullyIdempotent() {
    User existing = existingAdmin();
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(existing));
    given(adminCredentialRepository.findById(existing.getUserKey()))
        .willReturn(
            Optional.of(
                AdminAtlassianCredential.builder()
                    .userKey(existing.getUserKey())
                    .siteUrl(SITE_URL)
                    .adminApiToken(API_TOKEN)
                    .build()));

    seeder(configured()).seed();

    verify(userRepository, never()).save(any());
    verify(adminCredentialRepository, never()).save(any());
  }
}
