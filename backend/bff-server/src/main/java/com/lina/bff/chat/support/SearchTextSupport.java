package com.lina.bff.chat.support;

import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 대화 검색(Feature 7) 텍스트 처리 헬퍼. 검색어 정규식 메타문자 escape 와
 *           매칭 위치 주변 snippet/matchPositions 추출을 담당한다.
 *           하이라이트 HTML 은 만들지 않는다(XSS 안전성 — docs/api-spec.md §1-2).
 * 작성일 : 2026-06-08
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-08, 최초 작성, 2단계 Feature 7 — 검색어 escape + snippet 추출
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public final class SearchTextSupport {

  private static final String REGEX_METACHARACTERS = "\\^$.|?*+()[]{}";

  /** snippet 윈도우 좌/우 확장 폭(문자 수). */
  private static final int WINDOW = 40;

  private SearchTextSupport() {}

  /**
   * 매칭 위치 주변 발췌 결과.
   *
   * @param snippet 좌우 ~40자 발췌(잘림 시 {@code ...} 포함, plain text)
   * @param matchPositions {@code snippet} 기준 매칭 구간 {@code [[start, end], ...]} (UTF-16, end
   *     exclusive)
   */
  public record Snippet(String snippet, List<int[]> matchPositions) {}

  /**
   * 정규식 메타문자를 backslash escape 하여 검색어를 리터럴로 만든다.
   *
   * @param raw 사용자 검색어(trim 된 값)
   * @return 메타문자가 escape 된 정규식 안전 문자열
   */
  public static String escapeRegex(String raw) {
    StringBuilder escaped = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (REGEX_METACHARACTERS.indexOf(c) >= 0) {
        escaped.append('\\');
      }
      escaped.append(c);
    }
    return escaped.toString();
  }

  /**
   * 본문에서 검색어(대소문자 무시) 첫 매칭 위치 기준 좌우 ~40자를 발췌하고, 발췌 범위 내 모든 매칭 구간을 계산한다.
   *
   * @param content 메시지 본문
   * @param term 사용자 검색어(trim 된 값)
   * @return snippet 과 matchPositions
   */
  public static Snippet extract(String content, String term) {
    String lowerContent = content.toLowerCase();
    String lowerTerm = term.toLowerCase();
    int termLength = term.length();
    int firstMatch = lowerContent.indexOf(lowerTerm);
    if (firstMatch < 0 || termLength == 0) {
      // 메시지 단위 매칭 이후 호출되므로 정상 경로에서는 도달하지 않는다(방어적 처리).
      return new Snippet(content, List.of());
    }

    int start = Math.max(0, firstMatch - WINDOW);
    int end = Math.min(content.length(), firstMatch + termLength + WINDOW);
    String prefix = start > 0 ? "..." : "";
    String suffix = end < content.length() ? "..." : "";
    String snippet = prefix + content.substring(start, end) + suffix;

    List<int[]> matchPositions = new ArrayList<>();
    int offset = prefix.length() - start;
    for (int idx = lowerContent.indexOf(lowerTerm, start);
        idx >= 0 && idx + termLength <= end;
        idx = lowerContent.indexOf(lowerTerm, idx + termLength)) {
      int snippetStart = idx + offset;
      matchPositions.add(new int[] {snippetStart, snippetStart + termLength});
    }
    return new Snippet(snippet, matchPositions);
  }
}
