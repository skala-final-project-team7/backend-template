package com.lina.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FixedDemoUserProviderTest {

  @Test
  @DisplayName("설정값 fixed-user-id 를 그대로 반환한다")
  void shouldReturnConfiguredUserId() {
    FixedDemoUserProvider provider =
        new FixedDemoUserProvider("user-001", "Cloud-Control-Center", "CPC");

    assertThat(provider.getUserId()).isEqualTo("user-001");
  }

  @Test
  @DisplayName("단일 그룹 문자열을 단일 원소 리스트로 반환한다")
  void shouldReturnSingleGroupAsList() {
    FixedDemoUserProvider provider =
        new FixedDemoUserProvider("user-001", "Cloud-Control-Center", "CPC");

    assertThat(provider.getGroups()).containsExactly("Cloud-Control-Center");
  }

  @Test
  @DisplayName("콤마로 구분된 다중 그룹을 분리해 반환한다 (공백 trim)")
  void shouldSplitCommaSeparatedGroups() {
    FixedDemoUserProvider provider =
        new FixedDemoUserProvider("user-001", "Cloud-Control-Center, Platform-Ops , SRE", "CPC");

    assertThat(provider.getGroups()).containsExactly("Cloud-Control-Center", "Platform-Ops", "SRE");
  }

  @Test
  @DisplayName("빈/공백 그룹 설정은 빈 리스트로 처리한다")
  void shouldReturnEmptyListWhenGroupsBlank() {
    FixedDemoUserProvider provider = new FixedDemoUserProvider("user-001", "   ", "CPC");

    assertThat(provider.getGroups()).isEmpty();
  }

  @Test
  @DisplayName("설정값 fixed-space-key 를 그대로 반환한다")
  void shouldReturnConfiguredSpaceKey() {
    FixedDemoUserProvider provider =
        new FixedDemoUserProvider("user-001", "Cloud-Control-Center", "CPC");

    assertThat(provider.getSpaceKey()).isEqualTo("CPC");
  }
}
