package com.lina.bff.chat.dto;

import java.time.ZonedDateTime;

public record CreateConversationResponse(
    String conversationId, String title, boolean isPinned, ZonedDateTime createdAt) {}
