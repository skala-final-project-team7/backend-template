package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * AUTH-05 memberof 응답 1페이지. {@code totalSize > start + size} 이면 다음 페이지를 조회한다(전량 수집 — JWT groups
 * claim 누락 방지). JWT 에는 {@code results[].id}(=groupId)만 사용한다 — name 아님(RAG allowed_groups 정합).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroupMembershipPage(
    List<GroupResult> results, int start, int limit, int size, Integer totalSize) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GroupResult(String id, String name) {}
}
