package com.lina.bff.chat.dto;

import java.util.List;

/**
 * 대화 검색 응답.
 *
 * <p>{@code totalCount} 는 페이지와 무관한 매칭 대화 총 개수다. {@code results} 는 {@code lastMessageAt} 최신순(PoC —
 * 관련도 점수 미적용) 한 페이지 분량이다.
 */
public record ConversationSearchResponse(
    List<ConversationSearchResultResponse> results, long totalCount, int page, int size) {}
