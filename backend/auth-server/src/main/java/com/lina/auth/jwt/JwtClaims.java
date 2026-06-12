package com.lina.auth.jwt;

import java.util.List;

/**
 * LINA 세션 access JWT 의 서비스 claim 셋(와이어 표기 camelCase — backend/rules/auth.md §2).
 *
 * <p>JwtProvider 는 이 값을 입력받아 서명만 한다 — groups(=groupId 배열)·role 조회는 호출자(Feature 3 callback) 책임이며,
 * role 은 MySQL {@code users.role} 단일 source 값을 그대로 사용한다.
 *
 * @param userId Confluence accountId (이메일 아님)
 * @param groups Confluence groupId 배열 (memberof 결과, 빈 배열 허용)
 * @param role {@code USER} / {@code ADMIN}
 */
public record JwtClaims(String userId, List<String> groups, String role) {

  public JwtClaims {
    groups = List.copyOf(groups);
  }
}
