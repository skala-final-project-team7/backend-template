package com.lina.bff.admin.dto;

import java.time.Instant;

public record IngestJobCommand(
    String jobId, String adminUserId, String mode, Instant requestedAt) {}
