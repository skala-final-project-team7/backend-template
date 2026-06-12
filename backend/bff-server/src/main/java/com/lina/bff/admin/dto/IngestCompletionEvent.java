package com.lina.bff.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record IngestCompletionEvent(
    String jobId,
    String adminUserId,
    String mode,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completedAt,
    String errorCode,
    String message) {}
