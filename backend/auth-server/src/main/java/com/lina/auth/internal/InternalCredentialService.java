package com.lina.auth.internal;

import com.lina.auth.internal.dto.AdminConfluenceCredentialResponse;
import com.lina.auth.oauth.AtlassianOAuthClient;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.entity.UserToken;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.auth.token.repository.UserTokenRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Data Ingestion Worker 전용 admin Confluence credential 조회(Feature 5, api-spec §2-5).
 *           adminUserId(accountId) → role==ADMIN 검증 → user_tokens(OAuth accessToken/cloudId) +
 *           admin_atlassian_credential(site_url) 로드 → 만료/임박 시 AUTH-03 refresh 후 rotating
 *           덮어쓰기(이전 refresh 미보존). Atlassian 외부 호출은 트랜잭션 밖에서 수행하고 DB 갱신 구간만
 *           트랜잭션으로 묶는다(SessionService 와 동일 원칙). refreshToken·admin API Token 은 반환하지 않는다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 5 — 내부 credential 조회 API
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class InternalCredentialService {

  private static final Logger log = LoggerFactory.getLogger(InternalCredentialService.class);

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  /** 만료 "임박" 판정 여유 — Worker 가 받은 직후 Confluence 호출 중 만료되는 경계를 피한다. */
  private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

  private final AtlassianOAuthClient oauthClient;
  private final UserRepository userRepository;
  private final UserTokenRepository userTokenRepository;
  private final AdminAtlassianCredentialRepository adminCredentialRepository;
  private final PlatformTransactionManager transactionManager;

  public AdminConfluenceCredentialResponse getAdminCredential(String adminUserId) {
    User user =
        userRepository
            .findByUserId(adminUserId)
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    if (user.getRole() != UserRole.ADMIN) {
      throw new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다");
    }

    UserToken token =
        userTokenRepository
            .findById(user.getUserKey())
            .orElseThrow(
                () ->
                    new BizException(
                        ErrorCode.RESOURCE_NOT_FOUND, "저장된 Confluence credential 이 없습니다."));
    AdminAtlassianCredential adminCredential =
        adminCredentialRepository
            .findById(user.getUserKey())
            .orElseThrow(
                () ->
                    new BizException(
                        ErrorCode.RESOURCE_NOT_FOUND, "저장된 admin Atlassian credential 이 없습니다."));

    if (isExpiredOrImminent(token.getAccessTokenExpiresAt())) {
      token = refreshAndPersist(user, token.getConfluenceRefreshToken());
    }

    return new AdminConfluenceCredentialResponse(
        token.getConfluenceAccessToken(),
        token.getCloudId(),
        adminCredential.getSiteUrl(),
        toKstString(token.getAccessTokenExpiresAt()));
  }

  private boolean isExpiredOrImminent(Instant accessTokenExpiresAt) {
    return accessTokenExpiresAt.isBefore(Instant.now().plus(EXPIRY_SKEW));
  }

  /** AUTH-03 refresh(트랜잭션 밖 외부 호출) 후 rotating 덮어쓰기 — DB 갱신 구간만 트랜잭션. */
  private UserToken refreshAndPersist(User user, String confluenceRefreshToken) {
    AtlassianTokenResponse refreshed = callRefresh(user, confluenceRefreshToken);
    Instant expiresAt = Instant.now().plusSeconds(refreshed.expiresIn());

    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    return tx.execute(status -> persistRotatedToken(user.getUserKey(), refreshed, expiresAt));
  }

  private UserToken persistRotatedToken(
      UUID userKey, AtlassianTokenResponse refreshed, Instant expiresAt) {
    UserToken token =
        userTokenRepository
            .findById(userKey)
            .orElseThrow(
                () ->
                    new BizException(
                        ErrorCode.RESOURCE_NOT_FOUND, "저장된 Confluence credential 이 없습니다."));
    token.rotate(refreshed.accessToken(), refreshed.refreshToken(), expiresAt);
    userTokenRepository.save(token);
    return token;
  }

  private AtlassianTokenResponse callRefresh(User user, String confluenceRefreshToken) {
    try {
      return oauthClient.refreshAccessToken(confluenceRefreshToken);
    } catch (AtlassianOAuthClient.InvalidGrantException e) {
      // 재로그인 필요 상태 기록 — 토큰 원문은 로그 금지, 식별자만 남긴다
      log.warn("Confluence refresh invalid_grant — admin 재로그인 필요. userId={}", user.getUserId());
      throw new BizException(ErrorCode.UNAUTHORIZED, "Confluence 재로그인이 필요합니다.", e);
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Atlassian 토큰 갱신에 실패했습니다.", e);
    }
  }

  /** api-spec Common 시간 표기 — KST ISO-8601 offset (예: 2026-06-05T20:00:00+09:00). */
  private String toKstString(Instant instant) {
    return OffsetDateTime.ofInstant(instant, KST)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
