package com.lina.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA Authorization Server 부트스트랩. Confluence OAuth 2.0 및 JWT 발급 책임.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, AuthApplication 진입점 정의 및 common 패키지 스캔 포함
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x, Spring Security 6.3.x
 *   - jjwt 0.12.x
 * --------------------------------------------------
 * </pre>
 */
@SpringBootApplication(scanBasePackages = {"com.lina.auth", "com.lina.common"})
public class AuthApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthApplication.class, args);
  }
}
