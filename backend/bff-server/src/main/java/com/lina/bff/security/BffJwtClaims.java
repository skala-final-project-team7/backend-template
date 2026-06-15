package com.lina.bff.security;

import java.util.List;

/** BFF 에서 검증한 LINA access JWT claim 셋. */
public record BffJwtClaims(String userId, List<String> groups, String role) {

  public BffJwtClaims {
    groups = groups == null ? List.of() : List.copyOf(groups);
  }
}
