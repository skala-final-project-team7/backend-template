package com.lina.bff.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.user.dto.UserMeResponse;
import com.lina.bff.user.repository.UserProfileReadRepository;
import com.lina.bff.user.repository.UserProfileRow;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserMeServiceTest {

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private UserProfileReadRepository userProfileReadRepository;

  @Test
  @DisplayName("JWT userId 로 MySQL users row 를 조회해 KST 사용자 응답을 반환한다")
  void shouldReturnCurrentUserFromMysqlRow() {
    when(currentUserProvider.getUserId()).thenReturn("712020:abc");
    when(userProfileReadRepository.findByUserId("712020:abc"))
        .thenReturn(
            Optional.of(
                new UserProfileRow(
                    "712020:abc",
                    "홍길동",
                    "gildong@example.com",
                    "ADMIN",
                    "https://example.com/avatar.png",
                    Instant.parse("2026-06-15T09:00:00Z"))));

    UserMeResponse response =
        new UserMeService(currentUserProvider, userProfileReadRepository).getCurrentUser();

    assertThat(response.userId()).isEqualTo("712020:abc");
    assertThat(response.name()).isEqualTo("홍길동");
    assertThat(response.email()).isEqualTo("gildong@example.com");
    assertThat(response.role()).isEqualTo("ADMIN");
    assertThat(response.profileImageUrl()).isEqualTo("https://example.com/avatar.png");
    assertThat(response.lastLoginAt().toOffsetDateTime().toString())
        .isEqualTo("2026-06-15T18:00+09:00");
  }

  @Test
  @DisplayName("현재 userId 가 없으면 401 UNAUTHORIZED 를 던진다")
  void shouldRejectWhenCurrentUserIsMissing() {
    when(currentUserProvider.getUserId()).thenReturn("");

    assertThatThrownBy(
            () ->
                new UserMeService(currentUserProvider, userProfileReadRepository).getCurrentUser())
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("JWT userId 에 해당하는 MySQL row 가 없으면 404 를 던진다")
  void shouldRejectWhenUserRowIsMissing() {
    when(currentUserProvider.getUserId()).thenReturn("712020:missing");
    when(userProfileReadRepository.findByUserId("712020:missing")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                new UserMeService(currentUserProvider, userProfileReadRepository).getCurrentUser())
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }
}
