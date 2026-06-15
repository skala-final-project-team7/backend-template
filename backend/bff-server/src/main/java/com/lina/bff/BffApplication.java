package com.lina.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : LINA BFF Server 부트스트랩. Spring MVC + Virtual Threads 기반 단일 진입점 애플리케이션.
 * 작성일 : 2026-05-15
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-15, 최초 작성, BffApplication 진입점 정의 및 common 패키지 스캔 포함
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS — Virtual Threads (spring.threads.virtual.enabled=true) 사용
 *   - Spring Boot 3.3.x, Spring MVC 6.1.x
 *   - Spring Cloud Gateway Server MVC 4.1.x
 * --------------------------------------------------
 * </pre>
 */
@SpringBootApplication(
    scanBasePackages = {"com.lina.bff", "com.lina.common"},
    exclude = DataSourceAutoConfiguration.class)
public class BffApplication {

  public static void main(String[] args) {
    SpringApplication.run(BffApplication.class, args);
  }
}
