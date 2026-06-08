package com.lina.bff.chat.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.bff.chat.support.SearchTextSupport.Snippet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchTextSupportTest {

  @Test
  @DisplayName("정규식 메타문자는 backslash 로 escape 한다")
  void shouldEscapeRegexMetacharacters() {
    assertThat(SearchTextSupport.escapeRegex("a.b")).isEqualTo("a\\.b");
    assertThat(SearchTextSupport.escapeRegex("a*b+c?")).isEqualTo("a\\*b\\+c\\?");
    assertThat(SearchTextSupport.escapeRegex("(x)[y]{z}")).isEqualTo("\\(x\\)\\[y\\]\\{z\\}");
    assertThat(SearchTextSupport.escapeRegex("a|b\\c")).isEqualTo("a\\|b\\\\c");
    assertThat(SearchTextSupport.escapeRegex("^start$")).isEqualTo("\\^start\\$");
  }

  @Test
  @DisplayName("메타문자가 없는 일반 검색어는 그대로 유지한다")
  void shouldKeepPlainQueryUnchanged() {
    assertThat(SearchTextSupport.escapeRegex("S3 권한")).isEqualTo("S3 권한");
  }

  @Test
  @DisplayName("본문이 짧으면 잘림 없이 전체를 snippet 으로 반환하고 매칭 위치를 계산한다")
  void shouldReturnWholeContentWhenShort() {
    Snippet snippet = SearchTextSupport.extract("S3 권한 오류", "권한");

    assertThat(snippet.snippet()).isEqualTo("S3 권한 오류");
    assertThat(snippet.matchPositions()).hasSize(1);
    assertThat(snippet.matchPositions().get(0)).containsExactly(3, 5);
  }

  @Test
  @DisplayName("좌측이 잘리면 ... prefix 가 붙고 matchPositions 는 snippet 기준으로 재계산된다")
  void shouldPrefixEllipsisWhenLeftTruncated() {
    String content = "가".repeat(60) + "TARGET" + "나".repeat(5);

    Snippet snippet = SearchTextSupport.extract(content, "TARGET");

    assertThat(snippet.snippet()).startsWith("...");
    assertThat(snippet.snippet()).contains("TARGET");
    int start = snippet.matchPositions().get(0)[0];
    int end = snippet.matchPositions().get(0)[1];
    assertThat(snippet.snippet().substring(start, end)).isEqualTo("TARGET");
  }

  @Test
  @DisplayName("우측이 잘리면 ... suffix 가 붙는다")
  void shouldSuffixEllipsisWhenRightTruncated() {
    String content = "TARGET" + "나".repeat(60);

    Snippet snippet = SearchTextSupport.extract(content, "TARGET");

    assertThat(snippet.snippet()).endsWith("...");
    assertThat(snippet.snippet()).doesNotStartWith("...");
  }

  @Test
  @DisplayName("대소문자를 무시하고 매칭 위치를 찾는다")
  void shouldMatchCaseInsensitively() {
    Snippet snippet = SearchTextSupport.extract("hello S3 World", "s3");

    assertThat(snippet.matchPositions()).hasSize(1);
    int start = snippet.matchPositions().get(0)[0];
    int end = snippet.matchPositions().get(0)[1];
    assertThat(snippet.snippet().substring(start, end)).isEqualTo("S3");
  }

  @Test
  @DisplayName("snippet 윈도우 안의 모든 매칭 위치를 포함한다")
  void shouldIncludeAllMatchesWithinWindow() {
    Snippet snippet = SearchTextSupport.extract("S3 와 S3 비교", "S3");

    assertThat(snippet.matchPositions()).hasSize(2);
    assertThat(snippet.matchPositions().get(0)).containsExactly(0, 2);
    assertThat(snippet.matchPositions().get(1)).containsExactly(5, 7);
  }
}
