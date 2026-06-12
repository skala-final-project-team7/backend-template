package com.lina.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProperties;
import com.lina.auth.jwt.JwtProvider;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserGroup;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.UserGroupRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final String ACCOUNT_ID = "712020:abc";

  @Mock private JwtProvider jwtProvider;
  @Mock private UserRepository userRepository;
  @Mock private UserGroupRepository userGroupRepository;

  @Captor private ArgumentCaptor<JwtClaims> claimsCaptor;

  private SessionService service() {
    JwtProperties jwtProperties =
        new JwtProperties("lina-auth-server", "unused-private", "unused-public", 3600, 1209600);
    return new SessionService(jwtProvider, jwtProperties, userRepository, userGroupRepository);
  }

  private User userWithRefreshToken(String refreshToken, UserRole role) {
    User user =
        User.builder()
            .userId(ACCOUNT_ID)
            .email("dayeon@example.com")
            .name("이다연")
            .role(role)
            .accessToken("old-access")
            .build();
    user.storeRefreshToken(refreshToken);
    return user;
  }

  // --- refresh ---

  @Test
  @DisplayName("refresh: 저장값 일치 시 새 access/refresh 로 회전하고 DB 저장값을 덮어쓴다(이전 refresh 무효화)")
  void shouldRotateTokensOnRefresh() {
    User user = userWithRefreshToken("refresh-old", UserRole.ADMIN);
    given(jwtProvider.verifyRefreshToken("refresh-old")).willReturn(ACCOUNT_ID);
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(user));
    given(userGroupRepository.findByUserKey(user.getUserKey()))
        .willReturn(
            List.of(
                UserGroup.builder().userKey(user.getUserKey()).groupId("g-1").build(),
                UserGroup.builder().userKey(user.getUserKey()).groupId("g-2").build()));
    given(jwtProvider.issueAccessToken(any())).willReturn("new-access");
    given(jwtProvider.issueRefreshToken(ACCOUNT_ID)).willReturn("new-refresh");

    LoginTokenResponse response = service().refresh("refresh-old");

    assertThat(response.accessToken()).isEqualTo("new-access");
    assertThat(response.refreshToken()).isEqualTo("new-refresh");
    assertThat(OffsetDateTime.parse(response.expiresAt()).getOffset().getId()).isEqualTo("+09:00");

    // 권한 claim 은 refresh 시 DB 재조회 (JwtProvider 계약)
    verify(jwtProvider).issueAccessToken(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
        .isEqualTo(new JwtClaims(ACCOUNT_ID, List.of("g-1", "g-2"), "ADMIN"));

    // Rotating — 저장값 덮어쓰기(이전 refresh 재사용 불가)
    assertThat(user.getAccessToken()).isEqualTo("new-access");
    assertThat(user.getRefreshToken()).isEqualTo("new-refresh");
    verify(userRepository).save(user);
  }

  @Test
  @DisplayName("refresh: 서명 위조·만료 등 JWT 검증 실패는 401 UNAUTHORIZED")
  void shouldRejectInvalidRefreshJwt() {
    given(jwtProvider.verifyRefreshToken("expired-refresh")).willThrow(new JwtException("expired"));

    SessionService service = service();

    assertThatThrownBy(() -> service.refresh("expired-refresh"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
    verify(jwtProvider, never()).issueAccessToken(any());
  }

  @Test
  @DisplayName("refresh: 저장값과 불일치(회전 후 이전 토큰 재사용)는 401 — 새 토큰 미발급·저장값 불변")
  void shouldRejectReusedRefreshToken() {
    User user = userWithRefreshToken("refresh-current", UserRole.USER);
    given(jwtProvider.verifyRefreshToken("refresh-stolen-old")).willReturn(ACCOUNT_ID);
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(user));

    SessionService service = service();

    assertThatThrownBy(() -> service.refresh("refresh-stolen-old"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);

    verify(jwtProvider, never()).issueAccessToken(any());
    verify(jwtProvider, never()).issueRefreshToken(any());
    assertThat(user.getRefreshToken()).isEqualTo("refresh-current");
  }

  @Test
  @DisplayName("refresh: 저장값이 비어 있으면(로그아웃 상태) 401")
  void shouldRejectRefreshAfterLogout() {
    User user = userWithRefreshToken(null, UserRole.USER);
    given(jwtProvider.verifyRefreshToken("refresh-old")).willReturn(ACCOUNT_ID);
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(user));

    SessionService service = service();

    assertThatThrownBy(() -> service.refresh("refresh-old"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("refresh: 사용자 행이 없으면 401")
  void shouldRejectRefreshForUnknownUser() {
    given(jwtProvider.verifyRefreshToken("refresh-old")).willReturn(ACCOUNT_ID);
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());

    SessionService service = service();

    assertThatThrownBy(() -> service.refresh("refresh-old"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  // --- logout ---

  @Test
  @DisplayName("logout: users.refresh_token 저장값을 비워 이후 refresh 를 거부하게 한다")
  void shouldClearRefreshTokenOnLogout() {
    User user = userWithRefreshToken("refresh-current", UserRole.USER);
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.of(user));

    service().logout(ACCOUNT_ID);

    assertThat(user.getRefreshToken()).isNull();
    verify(userRepository).save(user);
  }

  @Test
  @DisplayName("logout: 사용자 행이 없으면 401")
  void shouldRejectLogoutForUnknownUser() {
    given(userRepository.findByUserId(ACCOUNT_ID)).willReturn(Optional.empty());

    SessionService service = service();

    assertThatThrownBy(() -> service.logout(ACCOUNT_ID))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }
}
