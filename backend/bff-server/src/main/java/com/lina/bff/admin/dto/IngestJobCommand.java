package com.lina.bff.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record IngestJobCommand(
    String jobId,
    String adminUserId,
    String mode,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant requestedAt) {}
