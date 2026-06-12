package com.lina.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthStateServiceTest {

  private static final long TTL_SECONDS = 600;

  @Test
  @DisplayName("state 생성→소비 라운드트립에서 mode/returnTo 가 보존된다")
  void shouldRoundTripModeAndReturnTo() {
    OAuthStateService stateService = service(TTL_SECONDS);

    String state = stateService.generate("admin", "/admin/dashboard");
    OAuthStateService.StateData data = stateService.consume(state);

    assertThat(state).isNotBlank();
    assertThat(data.mode()).isEqualTo("admin");
    assertThat(data.returnTo()).isEqualTo("/admin/dashboard");
  }

  @Test
  @DisplayName("발급된 적 없는 state 는 INVALID_REQUEST 로 거부한다")
  void shouldRejectUnknownState() {
    OAuthStateService stateService = service(TTL_SECONDS);

    assertThatThrownBy(() -> stateService.consume("forged-state"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("state 는 1회용 — 소비 후 재사용은 INVALID_REQUEST 로 거부한다")
  void shouldRejectReusedState() {
    OAuthStateService stateService = service(TTL_SECONDS);
    String state = stateService.generate(null, "/");
    stateService.consume(state);

    assertThatThrownBy(() -> stateService.consume(state))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("TTL 이 지난 state 는 INVALID_REQUEST 로 거부한다")
  void shouldRejectExpiredState() {
    OAuthStateService stateService = service(-1);
    String state = stateService.generate(null, "/");

    assertThatThrownBy(() -> stateService.consume(state))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("매 발급마다 서로 다른 추측 불가 state 를 생성한다")
  void shouldGenerateDistinctStates() {
    OAuthStateService stateService = service(TTL_SECONDS);

    String first = stateService.generate(null, "/");
    String second = stateService.generate(null, "/");

    assertThat(first).isNotEqualTo(second);
  }

  private OAuthStateService service(long ttlSeconds) {
    return new OAuthStateService(
        new OAuthProperties(
            "client-id",
            "client-secret",
            "https://auth.atlassian.com/authorize",
            "https://auth.atlassian.com/oauth/token",
            "https://api.atlassian.com/me",
            "https://app.example.com/auth/callback",
            "read:confluence-user offline_access",
            "https://api.atlassian.com",
            "",
            ttlSeconds));
  }
}
