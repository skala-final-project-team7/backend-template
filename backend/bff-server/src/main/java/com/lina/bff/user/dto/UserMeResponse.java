package com.lina.bff.user.dto;

import java.time.ZonedDateTime;

/** `GET /api/users/me` 응답 DTO. */
public record UserMeResponse(
    String userId,
    String name,
    String email,
    String role,
    String profileImageUrl,
    ZonedDateTime lastLoginAt) {}
