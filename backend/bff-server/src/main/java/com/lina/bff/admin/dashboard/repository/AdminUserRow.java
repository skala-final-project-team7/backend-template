package com.lina.bff.admin.dashboard.repository;

import java.time.Instant;
import java.util.UUID;

/** 관리자 사용자 현황 집계용 users read model. */
public record AdminUserRow(
    UUID userKey,
    String userId,
    String email,
    String name,
    String profileImageUrl,
    String role,
    Instant lastLoginAt,
    long groupCount) {}
