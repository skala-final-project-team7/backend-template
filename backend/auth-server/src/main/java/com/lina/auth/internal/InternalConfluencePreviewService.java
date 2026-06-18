package com.lina.auth.internal;

import com.lina.auth.internal.dto.ConfluencePagePreviewResponse;
import com.lina.auth.oauth.AtlassianOAuthClient;
import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.ConfluencePageV2Response;
import com.lina.auth.oauth.dto.ConfluenceSpaceV2Response;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserToken;
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
import java.util.ArrayList;
import java.util.List;
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
 * 작성목적 : Confluence 페이지 미리보기 내부 프록시(Feature P2, api-spec §4-3). userId(accountId) →
 *           user_tokens(OAuth accessToken/cloudId) 로드 → 만료/임박 시 AUTH-03 refresh 후 rotating
 *           덮어쓰기 → 사용자 본인 토큰으로 Confluence content 조회 → 미리보기 DTO 매핑. ACL 은 사용자 토큰
 *           호출로 Confluence 가 자연 강제(접근 불가=404). 토큰은 응답에 포함하지 않는다(미리보기 DTO 만).
 *           Atlassian 외부 호출은 트랜잭션 밖에서 수행하고 DB 갱신 구간만 트랜잭션으로 묶는다(Feature 5 와 동일 원칙).
 * 작성일 : 2026-06-18
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-18, 최초 작성, 4단계 Feature P2 — Confluence 미리보기 내부 프록시
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class InternalConfluencePreviewService {

  private static final Logger log = LoggerFactory.getLogger(InternalConfluencePreviewService.class);

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  /** 만료 "임박" 판정 여유 — 받은 직후 Confluence 호출 중 만료되는 경계를 피한다(Feature 5 동일). */
  private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

  private final AtlassianOAuthClient oauthClient;
  private final UserRepository userRepository;
  private final UserTokenRepository userTokenRepository;
  private final PlatformTransactionManager transactionManager;

  public ConfluencePagePreviewResponse getPagePreview(String userId, String pageId) {
    User user =
        userRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    UserToken token =
        userTokenRepository
            .findById(user.getUserKey())
            .orElseThrow(
                () ->
                    new BizException(
                        ErrorCode.RESOURCE_NOT_FOUND, "저장된 Confluence credential 이 없습니다."));

    if (isExpiredOrImminent(token.getAccessTokenExpiresAt())) {
      token = refreshAndPersist(user, token.getConfluenceRefreshToken());
    }

    ConfluencePageV2Response page = fetchPage(token, pageId);
    String spaceName = fetchSpaceName(token, page.spaceId());
    return mapToPreview(pageId, page, spaceName);
  }

  private ConfluencePageV2Response fetchPage(UserToken token, String pageId) {
    try {
      return oauthClient.fetchPageV2(token.getConfluenceAccessToken(), token.getCloudId(), pageId);
    } catch (AtlassianOAuthClient.ContentNotAccessibleException e) {
      // 없음(404)·접근 불가(403) 모두 존재 비노출을 위해 404 로 통일한다.
      throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Confluence 페이지 미리보기를 찾을 수 없습니다");
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Confluence 페이지 조회에 실패했습니다.", e);
    }
  }

  /**
   * spaceName 조회(breadcrumbs/표시용). 페이지는 떴는데 space 조회만 실패하면 미리보기 본문은 유효하므로 전체를 죽이지 않고 spaceName 을
   * null 로 두고 진행한다(best-effort).
   */
  private String fetchSpaceName(UserToken token, String spaceId) {
    if (spaceId == null) {
      return null;
    }
    try {
      ConfluenceSpaceV2Response space =
          oauthClient.fetchSpaceV2(token.getConfluenceAccessToken(), token.getCloudId(), spaceId);
      return space == null ? null : space.name();
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      log.warn("미리보기 spaceName 조회 실패 — spaceName 없이 진행. spaceId={}", spaceId);
      return null;
    }
  }

  /**
   * v2 page + spaceName → 미리보기 DTO. breadcrumbs 는 v2 ancestors 가 별도 scope 를 요구해(현재 미부여)
   * `[spaceName, title]` 로 구성한다(중간 조상 제목 생략 — 추후 parentId 순회로 보강 가능). authorName 은 v2 가 authorId 만
   * 주고 displayName 은 granular 모드에서 별도 user 조회가 막혀 현재 null 로 둔다.
   */
  private ConfluencePagePreviewResponse mapToPreview(
      String pageId, ConfluencePageV2Response page, String spaceName) {
    return new ConfluencePagePreviewResponse(
        pageId,
        page.title(),
        spaceName,
        null,
        resolveUpdatedAt(page),
        resolveBreadcrumbs(spaceName, page.title()),
        resolvePageUrl(page),
        resolveBody(page));
  }

  private List<String> resolveBreadcrumbs(String spaceName, String title) {
    List<String> breadcrumbs = new ArrayList<>();
    if (spaceName != null) {
      breadcrumbs.add(spaceName);
    }
    if (title != null) {
      breadcrumbs.add(title);
    }
    return breadcrumbs;
  }

  private String resolveUpdatedAt(ConfluencePageV2Response page) {
    String when = page.version() == null ? null : page.version().createdAt();
    return when == null ? null : toKstString(OffsetDateTime.parse(when).toInstant());
  }

  private String resolvePageUrl(ConfluencePageV2Response page) {
    if (page.links() == null) {
      return null;
    }
    String base = page.links().base();
    String webui = page.links().webui();
    if (base != null && webui != null) {
      return base + webui;
    }
    return webui != null ? webui : base;
  }

  private String resolveBody(ConfluencePageV2Response page) {
    if (page.body() == null || page.body().view() == null) {
      return null;
    }
    return page.body().view().value();
  }

  private boolean isExpiredOrImminent(Instant accessTokenExpiresAt) {
    return accessTokenExpiresAt.isBefore(Instant.now().plus(EXPIRY_SKEW));
  }

  /** AUTH-03 refresh(트랜잭션 밖 외부 호출) 후 rotating 덮어쓰기 — DB 갱신 구간만 트랜잭션(Feature 5 동일). */
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
      // 재로그인 필요 상태 — 토큰 원문은 로그 금지, 식별자만 남긴다(Feature 5 동일).
      log.warn("Confluence refresh invalid_grant — 재로그인 필요. userId={}", user.getUserId());
      throw new BizException(ErrorCode.UNAUTHORIZED, "Confluence 재로그인이 필요합니다.", e);
    } catch (AtlassianOAuthClient.AtlassianOAuthException e) {
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Atlassian 토큰 갱신에 실패했습니다.", e);
    }
  }

  /** api-spec Common 시간 표기 — KST ISO-8601 offset (예: 2026-04-15T18:30:00+09:00). */
  private String toKstString(Instant instant) {
    return OffsetDateTime.ofInstant(instant, KST)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
