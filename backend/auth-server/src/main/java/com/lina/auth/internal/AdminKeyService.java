package com.lina.auth.internal;

import com.lina.auth.internal.dto.AdminKeyActivateResponse;
import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Admin Key activate/deactivate 내부 API 비즈니스 로직(Feature 6). adminUserId(accountId)로
 *           role==ADMIN 검증 → admin_atlassian_credential(site_url + API Token) 로드 → AdminKeyClient
 *           로 Atlassian admin-key 호출. activate 는 미활성 확인 없이 반복 호출 안전(BFF 가 ingest 마다 호출).
 *           deactivate 는 jobId 기준 idempotent(중복 completion event 안전) — 이미 처리한 jobId 는 Atlassian
 *           DELETE 재호출 없이 성공 반환(단일 인스턴스 전제 in-memory TTL store). Atlassian 실패는 502.
 * 작성일 : 2026-06-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-15, 최초 작성, 3단계 Feature 6 — Admin Key 내부 API
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
public class AdminKeyService {

  private final AdminKeyClient adminKeyClient;
  private final UserRepository userRepository;
  private final AdminAtlassianCredentialRepository adminCredentialRepository;
  private final int durationMinutes;

  /**
   * deactivate 멱등 처리용 — 처리한 jobId 의 만료시각 보관(단일 인스턴스 전제, 다중 인스턴스 시 외부 저장소 교체). entry TTL 은 Admin Key
   * TTL 보다 길게 잡아(2배) 동일 job 의 중복 completion event 전 구간을 덮는다.
   */
  private final Map<String, Instant> deactivatedJobs = new ConcurrentHashMap<>();

  private final Duration jobTtl;

  public AdminKeyService(
      AdminKeyClient adminKeyClient,
      UserRepository userRepository,
      AdminAtlassianCredentialRepository adminCredentialRepository,
      @Value("${lina.admin-key.duration-minutes:60}") int durationMinutes) {
    this.adminKeyClient = adminKeyClient;
    this.userRepository = userRepository;
    this.adminCredentialRepository = adminCredentialRepository;
    this.durationMinutes = durationMinutes;
    this.jobTtl = Duration.ofMinutes(durationMinutes * 2L);
  }

  /** Admin Key 활성화 — 반복 호출 안전(미활성 확인 없이 매번 Atlassian activate). */
  public AdminKeyActivateResponse activate(String adminUserId, String jobId) {
    User admin = requireAdmin(adminUserId);
    AdminAtlassianCredential credential = loadCredential(admin.getUserKey());
    String expirationTime =
        callActivate(credential.getSiteUrl(), admin.getEmail(), credential.getAdminApiToken());
    return new AdminKeyActivateResponse(expirationTime);
  }

  /** Admin Key 비활성화 — jobId 기준 idempotent(중복 completion event 는 Atlassian DELETE 없이 성공). */
  public void deactivate(String adminUserId, String jobId) {
    if (isAlreadyDeactivated(jobId)) {
      return;
    }
    User admin = requireAdmin(adminUserId);
    AdminAtlassianCredential credential = loadCredential(admin.getUserKey());
    callDeactivate(credential.getSiteUrl(), admin.getEmail(), credential.getAdminApiToken());
    deactivatedJobs.put(jobId, Instant.now().plus(jobTtl));
  }

  private boolean isAlreadyDeactivated(String jobId) {
    Instant expiresAt = deactivatedJobs.get(jobId);
    if (expiresAt == null) {
      return false;
    }
    if (expiresAt.isBefore(Instant.now())) {
      deactivatedJobs.remove(jobId);
      return false;
    }
    return true;
  }

  /** admin-key 자격증명(§6.4) 로드. Basic auth = base64(admin email : 복호화된 API Token). */
  private AdminAtlassianCredential loadCredential(UUID userKey) {
    return adminCredentialRepository
        .findById(userKey)
        .orElseThrow(
            () ->
                new BizException(
                    ErrorCode.RESOURCE_NOT_FOUND, "저장된 admin Atlassian credential 이 없습니다."));
  }

  private User requireAdmin(String adminUserId) {
    User user =
        userRepository
            .findByUserId(adminUserId)
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    if (user.getRole() != UserRole.ADMIN) {
      throw new BizException(ErrorCode.FORBIDDEN, "관리자 권한이 없는 계정입니다");
    }
    return user;
  }

  private String callActivate(String siteUrl, String adminEmail, String adminApiToken) {
    try {
      return adminKeyClient.activate(siteUrl, adminEmail, adminApiToken, durationMinutes);
    } catch (AdminKeyClient.AdminKeyException e) {
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Admin Key 활성화에 실패했습니다.", e);
    }
  }

  private void callDeactivate(String siteUrl, String adminEmail, String adminApiToken) {
    try {
      adminKeyClient.deactivate(siteUrl, adminEmail, adminApiToken);
    } catch (AdminKeyClient.AdminKeyException e) {
      throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Admin Key 비활성화에 실패했습니다.", e);
    }
  }
}
