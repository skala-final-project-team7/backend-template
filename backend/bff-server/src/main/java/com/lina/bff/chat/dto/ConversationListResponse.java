package com.lina.bff.chat.dto;

import java.util.List;

public record ConversationListResponse(
    List<ConversationSummaryResponse> conversations, long totalCount, int page, int size) {}
