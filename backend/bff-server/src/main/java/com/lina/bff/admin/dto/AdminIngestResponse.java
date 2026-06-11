package com.lina.bff.admin.dto;

import java.time.Instant;

public record AdminIngestResponse(String jobId, String status, Instant startedAt) {}
