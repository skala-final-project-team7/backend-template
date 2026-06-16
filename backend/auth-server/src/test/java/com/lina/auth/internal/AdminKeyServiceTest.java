package com.lina.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lina.auth.internal.dto.AdminKeyActivateResponse;
import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Feature 6 — Admin Key activate/deactivate 비즈니스 로직 검증. role 분기·credential 로드·deactivate jobId
 * idempotency·에러 매핑(403/404/502)·activate 반복 호출 안전을 단위 수준에서 고정한다(Atlassian 실 호출 없음 — client mock).
 */
@ExtendWith(MockitoExtension.class)
class AdminKeyServiceTest {

  private static final String ADMIN_USER_ID = "712020:admin";
  private static final UUID USER_KEY = UUID.randomUUID();
  private static final String SITE_URL = "https://your-site.atlassian.net";
  private static final String ADMIN_EMAIL = "admin@example.com";
  private static final String ADMIN_API_TOKEN = "admin-api-token";
  private static final int DURATION_MINUTES = 60;

  @Mock private AdminKeyClient adminKeyClient;
  @Mock private UserRepository userRepository;
  @Mock private AdminAtlassianCredentialRepository adminCredentialRepository;

  private AdminKeyService service() {
    return new AdminKeyService(
        adminKeyClient, userRepository, adminCredentialRepository, DURATION_MINUTES);
  }

  private User user(UserRole role) {
    return User.builder()
        .userKey(USER_KEY)
        .userId(ADMIN_USER_ID)
        .email(ADMIN_EMAIL)
        .role(role)
        .accessToken("lina-access")
        .build();
  }

  private AdminAtlassianCredential adminCredential() {
    return AdminAtlassianCredential.builder()
        .userKey(USER_KEY)
        .siteUrl(SITE_URL)
        .adminApiToken(ADMIN_API_TOKEN)
        .build();
  }

  private void givenAdminWithCredential() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.ADMIN)));
    given(adminCredentialRepository.findById(USER_KEY)).willReturn(Optional.of(adminCredential()));
  }

  // --- activate ---

  @Test
  @DisplayName(
      "activate: role==ADMIN 이면 credential 로드 후 client.activate 를 60분으로 호출하고 expirationTime 을 반환")
  void shouldActivate() {
    givenAdminWithCredential();
    given(adminKeyClient.activate(SITE_URL, ADMIN_EMAIL, ADMIN_API_TOKEN, DURATION_MINUTES))
        .willReturn("2026-06-15T12:00:00.000Z");

    AdminKeyActivateResponse response = service().activate(ADMIN_USER_ID, "job-1");

    assertThat(response.expirationTime()).isEqualTo("2026-06-15T12:00:00.000Z");
    verify(adminKeyClient).activate(SITE_URL, ADMIN_EMAIL, ADMIN_API_TOKEN, 60);
  }

  @Test
  @DisplayName("activate: 동일 admin 반복 호출은 매번 Atlassian activate 를 호출한다(미활성 확인 없이 안전)")
  void shouldActivateRepeatedly() {
    givenAdminWithCredential();
    given(adminKeyClient.activate(anyString(), anyString(), anyString(), anyInt()))
        .willReturn("2026-06-15T12:00:00.000Z");

    service().activate(ADMIN_USER_ID, "job-1");
    service().activate(ADMIN_USER_ID, "job-1");

    verify(adminKeyClient, times(2)).activate(SITE_URL, ADMIN_EMAIL, ADMIN_API_TOKEN, 60);
  }

  @Test
  @DisplayName("activate: role != ADMIN 은 403, credential 로드·Atlassian 호출 없음")
  void shouldRejectNonAdminActivate() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.USER)));

    assertThatThrownBy(() -> service().activate(ADMIN_USER_ID, "job-1"))
        .isInstanceOfSatisfying(
            BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(adminCredentialRepository, never()).findById(any());
    verify(adminKeyClient, never()).activate(anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  @DisplayName("activate: 사용자가 없으면 404")
  void shouldThrow404WhenUserMissing() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().activate(ADMIN_USER_ID, "job-1"))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName("activate: admin_atlassian_credential 이 없으면 404")
  void shouldThrow404WhenCredentialMissing() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.ADMIN)));
    given(adminCredentialRepository.findById(USER_KEY)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service().activate(ADMIN_USER_ID, "job-1"))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName("activate: Atlassian 호출 실패는 502 EXTERNAL_SERVICE_ERROR")
  void shouldMapActivateFailureTo502() {
    givenAdminWithCredential();
    given(adminKeyClient.activate(anyString(), anyString(), anyString(), anyInt()))
        .willThrow(new AdminKeyClient.AdminKeyException("Atlassian 5xx"));

    assertThatThrownBy(() -> service().activate(ADMIN_USER_ID, "job-1"))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
  }

  // --- deactivate ---

  @Test
  @DisplayName("deactivate: 첫 호출은 client.deactivate 를 호출한다")
  void shouldDeactivate() {
    givenAdminWithCredential();

    service().deactivate(ADMIN_USER_ID, "job-1");

    verify(adminKeyClient).deactivate(SITE_URL, ADMIN_EMAIL, ADMIN_API_TOKEN);
  }

  @Test
  @DisplayName("deactivate: 동일 jobId 중복은 Atlassian DELETE 없이 idempotent 성공(2번째는 client 미호출)")
  void shouldBeIdempotentOnDuplicateJobId() {
    givenAdminWithCredential();

    AdminKeyService service = service();
    service.deactivate(ADMIN_USER_ID, "job-1");
    service.deactivate(ADMIN_USER_ID, "job-1");

    verify(adminKeyClient, times(1)).deactivate(SITE_URL, ADMIN_EMAIL, ADMIN_API_TOKEN);
  }

  @Test
  @DisplayName("deactivate: 다른 jobId 는 각각 Atlassian DELETE 호출")
  void shouldDeactivateDistinctJobs() {
    givenAdminWithCredential();

    AdminKeyService service = service();
    service.deactivate(ADMIN_USER_ID, "job-1");
    service.deactivate(ADMIN_USER_ID, "job-2");

    verify(adminKeyClient, times(2)).deactivate(SITE_URL, ADMIN_EMAIL, ADMIN_API_TOKEN);
  }

  @Test
  @DisplayName("deactivate: role != ADMIN 은 403")
  void shouldRejectNonAdminDeactivate() {
    given(userRepository.findByUserId(ADMIN_USER_ID)).willReturn(Optional.of(user(UserRole.USER)));

    assertThatThrownBy(() -> service().deactivate(ADMIN_USER_ID, "job-1"))
        .isInstanceOfSatisfying(
            BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    verify(adminKeyClient, never()).deactivate(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("deactivate: Atlassian 호출 실패는 502 EXTERNAL_SERVICE_ERROR")
  void shouldMapDeactivateFailureTo502() {
    givenAdminWithCredential();
    org.mockito.BDDMockito.willThrow(new AdminKeyClient.AdminKeyException("Atlassian 5xx"))
        .given(adminKeyClient)
        .deactivate(eq(SITE_URL), eq(ADMIN_EMAIL), eq(ADMIN_API_TOKEN));

    assertThatThrownBy(() -> service().deactivate(ADMIN_USER_ID, "job-1"))
        .isInstanceOfSatisfying(
            BizException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
  }
}
