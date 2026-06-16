package com.lina.auth.oauth;

import com.lina.auth.oauth.dto.AtlassianTokenResponse;
import com.lina.auth.oauth.dto.AtlassianUserInfo;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserGroup;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.entity.UserToken;
import com.lina.auth.token.repository.UserGroupRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.auth.token.repository.UserTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** users/user_groups/user_tokens 영속화 책임을 담당한다. */
@Service
@RequiredArgsConstructor
public class OAuthLoginPersistenceService {

  private final UserRepository userRepository;
  private final UserGroupRepository userGroupRepository;
  private final UserTokenRepository userTokenRepository;

  @Transactional
  public void persistLoginState(
      Optional<User> existingUser,
      AtlassianUserInfo userInfo,
      String accessJwt,
      String refreshToken,
      List<String> groupIds,
      AtlassianTokenResponse confluenceTokens,
      String cloudId,
      Instant now) {
    User user = upsertUser(existingUser, userInfo, accessJwt, refreshToken, now);
    replaceGroups(user.getUserKey(), groupIds);
    saveConfluenceTokens(user.getUserKey(), confluenceTokens, cloudId, now);
  }

  private User upsertUser(
      Optional<User> existingUser,
      AtlassianUserInfo userInfo,
      String accessJwt,
      String refreshToken,
      Instant now) {
    User user =
        existingUser
            .map(
                existing -> {
                  existing.updateOnLogin(userInfo.name(), userInfo.picture(), accessJwt, now);
                  return existing;
                })
            .orElseGet(
                () ->
                    User.builder()
                        .userId(userInfo.accountId())
                        .email(userInfo.email())
                        .name(userInfo.name())
                        .profileImageUrl(userInfo.picture())
                        .role(UserRole.USER)
                        .accessToken(accessJwt)
                        .lastLoginAt(now)
                        .build());
    user.storeRefreshToken(refreshToken);
    userRepository.save(user);
    return user;
  }

  /** 로그인 시 기존 멤버십을 삭제하고 memberof 결과로 교체 적재한다(docs/db-schema.md §6.3). */
  private void replaceGroups(UUID userKey, List<String> groupIds) {
    userGroupRepository.deleteByUserKey(userKey);
    if (!groupIds.isEmpty()) {
      userGroupRepository.saveAll(
          groupIds.stream()
              .map(groupId -> UserGroup.builder().userKey(userKey).groupId(groupId).build())
              .toList());
    }
  }

  /** Confluence OAuth 토큰 + cloudId 암호화 저장. 기존 행이 있으면 rotate 로 덮어쓴다(이전 값 미보존). */
  private void saveConfluenceTokens(
      UUID userKey, AtlassianTokenResponse confluenceTokens, String cloudId, Instant now) {
    Instant expiresAt = now.plusSeconds(confluenceTokens.expiresIn());
    userTokenRepository
        .findById(userKey)
        .ifPresentOrElse(
            token ->
                token.rotate(
                    confluenceTokens.accessToken(), confluenceTokens.refreshToken(), expiresAt),
            () ->
                userTokenRepository.save(
                    UserToken.builder()
                        .userKey(userKey)
                        .confluenceAccessToken(confluenceTokens.accessToken())
                        .confluenceRefreshToken(confluenceTokens.refreshToken())
                        .cloudId(cloudId)
                        .accessTokenExpiresAt(expiresAt)
                        .build()));
  }
}
