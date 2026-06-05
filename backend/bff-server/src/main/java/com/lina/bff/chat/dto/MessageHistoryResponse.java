package com.lina.bff.chat.dto;

import java.util.List;

public record MessageHistoryResponse(String conversationId, List<MessageResponse> messages) {}
