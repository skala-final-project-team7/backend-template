package com.lina.auth.token;

import com.lina.auth.token.entity.AdminAtlassianCredential;
import com.lina.auth.token.entity.User;
import com.lina.auth.token.entity.UserRole;
import com.lina.auth.token.repository.AdminAtlassianCredentialRepository;
import com.lina.auth.token.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 최초 admin 사전 seed(로그인 전 주입, docs/db-schema.md §6.1). 부팅 시 env(AdminSeedProperties)
 *           가 설정돼 있으면 users(role=ADMIN, access_token='ADMIN_PLACEHOLDER') 와
 *           admin_atlassian_credential(site_url + 평문 API Token→저장 시 TokenCipher 암호화)을 멱등 INSERT 한다.
 *           access_token 은 NOT NULL 이라 로그인 전 placeholder 더미값(첫 로그인 시 실제 JWT 로 덮어씀).
 *           user_tokens(OAuth)는 로그인 산물이라 seed 대상이 아니다 → Feature 6(admin-key)은 seed 만으로
 *           동작, Feature 5(credential 조회)는 admin 1회 로그인 후 동작. 미설정·기존행이면 건너뛴다.
 * 작성일 : 2026-06-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-15, 최초 작성, 3단계 admin seed
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x (ApplicationRunner — 컨텍스트 기동 완료 후 1회 실행)
 * --------------------------------------------------
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {
  private final AdminSeedService seedService;

  @Override
  public void run(ApplicationArguments args) {
    seedService.seed();
  }
}
