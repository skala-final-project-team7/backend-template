package com.lina.bff.admin.dashboard.dto;

import java.time.ZonedDateTime;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 데이터 현황 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 5 — /api/admin/data 응답 모델 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
public record AdminDataResponse(
    long totalSpaces,
    long totalPages,
    long totalAttachments,
    String vectorDbSize,
    long totalChunks,
    ZonedDateTime lastSyncAt) {}
