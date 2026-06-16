package com.lina.auth.support;

/**
 * 토큰·secret 값을 로그/toString 에 원문 노출하지 않도록 마스킹한다(backend/auth-server/CLAUDE.md §3.1 — 로그에 토큰/secret
 * 원문 금지). null 은 그대로 두어 '없음'과 '가려짐'을 구분할 수 있게 한다.
 */
public final class SensitiveValues {

  private static final String MASK = "***";

  private SensitiveValues() {}

  /** 값이 있으면 {@code ***} 로 가리고, null 이면 null 을 그대로 반환한다. */
  public static String mask(String value) {
    return value == null ? null : MASK;
  }
}
