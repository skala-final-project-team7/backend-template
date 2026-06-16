package com.lina.auth.token.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;

  private User user(String userId, String email) {
    return User.builder()
        .userId(userId)
        .email(email)
        .name("홍길동")
        .role(UserRole.ADMIN)
        .accessToken("lina-access-token")
        .build();
  }

  @Test
  @DisplayName("user_id(Confluence accountId)로 사용자를 조회하면 저장된 role 이 반환된다")
  void shouldLookupRoleByUserId() {
    userRepository.save(user("712020:abc", "admin@example.com"));

    User found = userRepository.findByUserId("712020:abc").orElseThrow();

    assertThat(found.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(found.getEmail()).isEqualTo("admin@example.com");
  }

  @Test
  @DisplayName("존재하지 않는 user_id 조회는 empty 를 반환한다")
  void shouldReturnEmptyForUnknownUserId() {
    assertThat(userRepository.findByUserId("712020:absent")).isEmpty();
  }

  @Test
  @DisplayName("기존 사용자 upsert 시 user_key·role 은 유지되고 프로필·세션 토큰·로그인 시각만 갱신된다")
  void shouldUpdateExistingUserOnLogin() {
    User saved = userRepository.saveAndFlush(user("712020:abc", "admin@example.com"));
    UUID originalKey = saved.getUserKey();
    Instant loginAt = Instant.parse("2026-06-11T00:00:00Z");

    saved.updateOnLogin("새이름", "https://img.example.com/p.png", "new-lina-access", loginAt);
    userRepository.saveAndFlush(saved);
    entityManager.clear();

    User found = userRepository.findByUserId("712020:abc").orElseThrow();
    assertThat(found.getUserKey()).isEqualTo(originalKey);
    assertThat(found.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(found.getName()).isEqualTo("새이름");
    assertThat(found.getProfileImageUrl()).isEqualTo("https://img.example.com/p.png");
    assertThat(found.getAccessToken()).isEqualTo("new-lina-access");
    assertThat(found.getLastLoginAt()).isEqualTo(loginAt);
    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("RS256 JWT 실측 길이(512자 초과)의 LINA 세션 access/refresh 토큰이 저장된다")
  void shouldStoreRealisticLengthSessionTokens() {
    String longAccessJwt = "a".repeat(1500);
    String longRefreshJwt = "r".repeat(700);
    userRepository.saveAndFlush(
        User.builder()
            .userId("712020:abc")
            .email("admin@example.com")
            .role(UserRole.USER)
            .accessToken(longAccessJwt)
            .refreshToken(longRefreshJwt)
            .build());
    entityManager.clear();

    User found = userRepository.findByUserId("712020:abc").orElseThrow();
    assertThat(found.getAccessToken()).isEqualTo(longAccessJwt);
    assertThat(found.getRefreshToken()).isEqualTo(longRefreshJwt);
  }

  @Test
  @DisplayName("동일 user_id 의 중복 INSERT 는 거부된다(uk_users_user_id)")
  void shouldRejectDuplicateUserId() {
    userRepository.saveAndFlush(user("712020:abc", "first@example.com"));

    assertThatThrownBy(() -> userRepository.saveAndFlush(user("712020:abc", "second@example.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("동일 email 의 중복 INSERT 는 거부된다(uk_users_email)")
  void shouldRejectDuplicateEmail() {
    userRepository.saveAndFlush(user("712020:abc", "same@example.com"));

    assertThatThrownBy(() -> userRepository.saveAndFlush(user("712020:def", "same@example.com")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
