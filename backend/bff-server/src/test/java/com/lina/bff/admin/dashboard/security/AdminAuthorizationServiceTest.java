package com.lina.bff.admin.dashboard.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthorizationServiceTest {

  @Mock private CurrentUserProvider currentUserProvider;

  @Test
  @DisplayName("ADMIN role 이면 예외 없이 통과한다")
  void shouldAllowAdminRole() {
    when(currentUserProvider.getUserId()).thenReturn("admin-account-id");
    when(currentUserProvider.getRole()).thenReturn("ADMIN");

    AdminAuthorizationService service = new AdminAuthorizationService(currentUserProvider);

    service.requireAdmin();
  }

  @Test
  @DisplayName("사용자 식별자가 없으면 401 UNAUTHORIZED 로 차단한다")
  void shouldRejectMissingUserId() {
    when(currentUserProvider.getUserId()).thenReturn(" ");

    AdminAuthorizationService service = new AdminAuthorizationService(currentUserProvider);

    assertThatThrownBy(service::requireAdmin)
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("ADMIN 이 아닌 role 은 403 FORBIDDEN 으로 차단한다")
  void shouldRejectNonAdminRole() {
    when(currentUserProvider.getUserId()).thenReturn("user-account-id");
    when(currentUserProvider.getRole()).thenReturn("USER");

    AdminAuthorizationService service = new AdminAuthorizationService(currentUserProvider);

    assertThatThrownBy(service::requireAdmin)
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FORBIDDEN);
  }
}
