package com.lina.auth.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveValuesTest {

  @Test
  @DisplayName("값이 있으면 *** 로 가린다 (원문 미노출)")
  void masksNonNullValue() {
    assertThat(SensitiveValues.mask("super-secret-token")).isEqualTo("***");
  }

  @Test
  @DisplayName("null 은 그대로 둔다 — '없음'과 '가려짐'을 구분한다")
  void keepsNull() {
    assertThat(SensitiveValues.mask(null)).isNull();
  }
}
