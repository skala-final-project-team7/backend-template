package com.lina.bff.security;

import com.lina.bff.config.CurrentUserProvider;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** SecurityContext 에 적재된 JWT claim 기반 현재 사용자 공급자. */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

  @Override
  public String getUserId() {
    BffJwtClaims claims = currentClaims();
    return claims == null ? "" : claims.userId();
  }

  @Override
  public List<String> getGroups() {
    BffJwtClaims claims = currentClaims();
    return claims == null ? List.of() : claims.groups();
  }

  @Override
  public String getSpaceKey() {
    return "";
  }

  @Override
  public String getRole() {
    BffJwtClaims claims = currentClaims();
    return claims == null ? "" : claims.role();
  }

  private BffJwtClaims currentClaims() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof BffJwtClaims claims)) {
      return null;
    }
    return claims;
  }
}
