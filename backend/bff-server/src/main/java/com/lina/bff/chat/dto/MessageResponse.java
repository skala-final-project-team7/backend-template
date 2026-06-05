package com.lina.bff.chat.dto;

import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.entity.VerificationResult;
import java.time.ZonedDateTime;
import java.util.List;

public record MessageResponse(
    String messageId,
    MessageRole role,
    String content,
    List<SourceResponse> sources,
    Double confidenceScore,
    VerificationResult verificationResult,
    ZonedDateTime createdAt) {}
