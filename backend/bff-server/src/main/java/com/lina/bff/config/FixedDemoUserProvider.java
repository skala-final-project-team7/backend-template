package com.lina.bff.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 중간발표(인증 없음) 한정 고정 데모 사용자/그룹/스페이스 공급자.
 *           lina.demo.* 설정값을 읽어 {@link CurrentUserProvider} 를 만족시킨다.
 *           3단계에서 JWT Claim 기반 구현체로 교체되며 본 클래스는 삭제 대상이다.
 * 작성일 : 2026-05-21
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-21, 최초 작성, 2단계 Feature 2 — 고정 데모 사용자 컴포넌트
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Component
public class FixedDemoUserProvider implements CurrentUserProvider {

  private final String userId;
  private final List<String> groups;
  private final String spaceKey;

  public FixedDemoUserProvider(
      @Value("${lina.demo.fixed-user-id}") String userId,
      @Value("${lina.demo.fixed-groups}") String groupsCsv,
      @Value("${lina.demo.fixed-space-key}") String spaceKey) {
    this.userId = userId;
    this.groups = parseGroups(groupsCsv);
    this.spaceKey = spaceKey;
  }

  private static List<String> parseGroups(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  @Override
  public String getUserId() {
    return userId;
  }

  @Override
  public List<String> getGroups() {
    return groups;
  }

  @Override
  public String getSpaceKey() {
    return spaceKey;
  }
}
