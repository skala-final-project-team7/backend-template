package com.lina.bff.chat.dto;

import java.time.ZonedDateTime;

public record UpdateConversationResponse(
    String conversationId, String title, boolean isPinned, ZonedDateTime updatedAt) {}
