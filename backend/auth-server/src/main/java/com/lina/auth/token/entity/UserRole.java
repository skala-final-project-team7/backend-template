package com.lina.auth.token.entity;

/**
 * 사용자 권한 역할. MySQL {@code users.role} ENUM 과 동일 표기이며 JWT {@code role} claim 의 single source of
 * truth 다 (docs/db-schema.md §6.1).
 */
public enum UserRole {
  USER,
  ADMIN
}
