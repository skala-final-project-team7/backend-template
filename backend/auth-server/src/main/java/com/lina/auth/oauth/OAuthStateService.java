package com.lina.auth.oauth;

import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : OAuth state(CSRF) 발급/검증. login 의 mode/returnTo 를 state 에 직렬화해 callback 까지
 *           서버 측에 보관한다(클라이언트 조작으로 admin 우회 방지 — docs/api-spec.md §4-1).
 *           state 는 1회용 — 소비 즉시 제거되어 재사용(replay)을 거부한다. PoC 는 단일 인스턴스
 *           전제의 in-memory 보관(서버 세션 방식)이며, 다중 인스턴스 전개 시 외부 저장소로 교체한다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

  private static final int STATE_BYTES = 32;

  private final SecureRandom secureRandom = new SecureRandom();
  private final Map<String, StoredState> store = new ConcurrentHashMap<>();
  private final OAuthProperties properties;

  /** state 발급 + mode/returnTo 보관. */
  public String generate(String mode, String returnTo) {
    purgeExpired();
    byte[] bytes = new byte[STATE_BYTES];
    secureRandom.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    store.put(
        state,
        new StoredState(
            new StateData(mode, returnTo),
            Instant.now().plusSeconds(properties.getStateTtlSeconds())));
    return state;
  }

  /** state 검증·소비(1회용). 미발급/만료/재사용 시 400 INVALID_REQUEST. */
  public StateData consume(String state) {
    StoredState stored = state == null ? null : store.remove(state);
    if (stored == null || Instant.now().isAfter(stored.expiresAt())) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "유효하지 않은 state 입니다.");
    }
    return stored.data();
  }

  private void purgeExpired() {
    Instant now = Instant.now();
    store.values().removeIf(stored -> now.isAfter(stored.expiresAt()));
  }

  private record StoredState(StateData data, Instant expiresAt) {}

  /** login 1사이클 동안 서버가 보관하는 state 부가 정보. */
  public record StateData(String mode, String returnTo) {}
}
