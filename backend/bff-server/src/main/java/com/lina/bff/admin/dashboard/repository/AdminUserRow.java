package com.lina.bff.admin.dashboard.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 관리자 사용자 현황 집계용 users read model. groupIds 는 ACL(접근 가능 페이지) 매칭에 사용. */
public record AdminUserRow(
    UUID userKey,
    String userId,
    String email,
    String name,
    String profileImageUrl,
    String role,
    Instant lastLoginAt,
    List<String> groupIds) {}
