package com.lina.bff.chat.dto;

import com.lina.bff.chat.entity.MessageRole;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 대화 검색 결과의 매칭 메시지 한 건.
 *
 * <p>{@code snippet} 은 매칭 위치 주변 발췌(plain text)이며, 본문이 잘린 경우 {@code ...} 가 포함된다. {@code
 * matchPositions} 는 {@code snippet} 문자열 기준 매칭 구간 {@code [[start, end], ...]} 배열로, 인덱스는 UTF-16 code
 * unit, {@code end} 는 exclusive 다(JS {@code String.slice} 호환). 하이라이트 HTML 은 서버가 생성하지 않는다(XSS 안전성 —
 * {@code docs/api-spec.md} §1-2).
 */
public record MatchedMessageResponse(
    String messageId,
    MessageRole role,
    String snippet,
    List<int[]> matchPositions,
    ZonedDateTime createdAt) {}
