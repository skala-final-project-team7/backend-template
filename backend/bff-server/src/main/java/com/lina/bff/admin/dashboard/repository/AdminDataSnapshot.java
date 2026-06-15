package com.lina.bff.admin.dashboard.repository;

import java.time.Instant;

/** 관리자 데이터 현황 집계용 MongoDB read model. */
public record AdminDataSnapshot(
    long totalSpaces,
    long totalPages,
    long totalAttachments,
    long totalChunks,
    Instant lastSyncAt) {}
