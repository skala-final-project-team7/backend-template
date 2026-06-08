package com.lina.bff.chat.dto;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 대화 검색 결과의 대화 단위 항목.
 *
 * <p>{@code matchedMessages} 는 매칭 메시지 샘플로 대화당 최대 3건까지 노출하며, 더 많은 매칭은 {@code matchCount}(해당 대화 내 매칭
 * 메시지 총수)로 표기한다.
 */
public record ConversationSearchResultResponse(
    String conversationId,
    String title,
    ZonedDateTime lastMessageAt,
    boolean isPinned,
    List<MatchedMessageResponse> matchedMessages,
    int matchCount) {}
