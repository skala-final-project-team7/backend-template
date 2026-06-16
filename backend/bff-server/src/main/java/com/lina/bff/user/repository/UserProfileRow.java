package com.lina.bff.user.repository;

import java.time.Instant;

/** MySQL `users` read model for the authenticated user profile. */
public record UserProfileRow(
    String userId,
    String name,
    String email,
    String role,
    String profileImageUrl,
    Instant lastLoginAt) {}
