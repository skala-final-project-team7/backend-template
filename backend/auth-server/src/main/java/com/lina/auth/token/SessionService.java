package com.lina.auth.token;

import com.lina.auth.jwt.JwtClaims;
import com.lina.auth.jwt.JwtProperties;
import com.lina.auth.jwt.JwtProvider;
import com.lina.auth.oauth.dto.LoginTokenResponse;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserGroup;
import com.lina.auth.token.repository.UserGroupRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA 세션 refresh 회전/무효화(Feature 4). refresh 는 JWT 검증(서명·만료·tokenType) 후
 *           users.refresh_token 저장값과 대조한다 — stateless 검증만으론 회전 후 이전 토큰 재사용을
 *           잡지 못하므로 저장값 단일 대조가 재사용 거부의 근거다(별도 토큰 테이블 미사용).
 *           Rotating: 갱신 시 새 access/refresh 로 저장값을 덮어쓰고, logout 은 저장값을 비운다.
 *           권한 claim(groups/role)은 refresh 시 DB 재조회한다(JwtProvider 계약).
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 4 — 세션 관리 (docs/api-spec.md §4-1)
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class SessionService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final UserRepository userRepository;
  private final UserGroupRepository userGroupRepository;
  private final PlatformTransactionManager transactionManager;

  /** Rotating refresh: 검증·저장값 대조 후 새 access/refresh 발급, 이전 refresh 무효화. 실패 시 401. */
  public LoginTokenResponse refresh(String refreshToken) {
    String userId = verifyRefreshJwt(refreshToken);
    Instant now = Instant.now();

    SessionTokens sessionTokens = rotateSession(userId, refreshToken);

    return new LoginTokenResponse(
        sessionTokens.accessToken(), sessionTokens.refreshToken(), accessTokenExpiresAt(now));
  }

  /** logout: users.refresh_token 저장값을 비워 이후 refresh 를 거부한다. */
  @Transactional
  public void logout(String userId) {
    User user = findUserOr401(userId);
    user.clearRefreshToken();
    userRepository.save(user);
  }

  private String verifyRefreshJwt(String refreshToken) {
    try {
      return jwtProvider.verifyRefreshToken(refreshToken);
    } catch (JwtException e) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "유효하지 않은 refresh token 입니다.", e);
    }
  }

  private User findUserOr401(String userId) {
    return userRepository
        .findByUserId(userId)
        .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "유효하지 않은 사용자입니다."));
  }

  /** access JWT 만료 시각(KST, ISO-8601 offset — docs/api-spec.md §4-1 expiresAt). */
  private String accessTokenExpiresAt(Instant issuedAt) {
    return OffsetDateTime.ofInstant(
            issuedAt.plusSeconds(jwtProperties.getAccessTokenTtlSeconds()), KST)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private SessionTokens rotateSession(String userId, String refreshToken) {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    return tx.execute(
        status -> {
          User user = findUserOr401(userId);
          if (user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)) {
            // 회전 후 이전 토큰 재사용·logout 상태 — stateless 검증으로는 못 잡는 케이스
            throw new BizException(ErrorCode.UNAUTHORIZED, "유효하지 않은 refresh token 입니다.");
          }

          List<String> groupIds =
              userGroupRepository.findByUserKey(user.getUserKey()).stream()
                  .map(UserGroup::getGroupId)
                  .toList();
          String newAccessToken =
              jwtProvider.issueAccessToken(new JwtClaims(userId, groupIds, user.getRole().name()));
          String newRefreshToken = jwtProvider.issueRefreshToken(userId);

          user.rotateSessionTokens(newAccessToken, newRefreshToken);
          userRepository.save(user);

          return new SessionTokens(newAccessToken, newRefreshToken);
        });
  }

  private record SessionTokens(String accessToken, String refreshToken) {}
}
