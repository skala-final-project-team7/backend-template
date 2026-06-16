package com.lina.bff.user.service;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.user.dto.UserMeResponse;
import com.lina.bff.user.repository.UserProfileReadRepository;
import com.lina.bff.user.repository.UserProfileRow;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;

@Service
public class UserMeService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CurrentUserProvider currentUserProvider;
  private final UserProfileReadRepository userProfileReadRepository;

  public UserMeService(
      CurrentUserProvider currentUserProvider,
      UserProfileReadRepository userProfileReadRepository) {
    this.currentUserProvider = currentUserProvider;
    this.userProfileReadRepository = userProfileReadRepository;
  }

  public UserMeResponse getCurrentUser() {
    String userId = currentUserProvider.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.");
    }

    UserProfileRow user =
        userProfileReadRepository
            .findByUserId(userId)
            .orElseThrow(
                () -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

    return new UserMeResponse(
        user.userId(),
        user.name(),
        user.email(),
        user.role(),
        user.profileImageUrl(),
        user.lastLoginAt() == null ? null : ZonedDateTime.ofInstant(user.lastLoginAt(), KST));
  }
}
