package com.lina.bff.chat.dto;

import java.time.ZonedDateTime;

public record ConversationSummaryResponse(
    String conversationId, String title, ZonedDateTime lastMessageAt, boolean isPinned) {}
