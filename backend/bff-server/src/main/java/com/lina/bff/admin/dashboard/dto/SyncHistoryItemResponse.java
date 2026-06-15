package com.lina.bff.admin.dashboard.dto;

import java.time.ZonedDateTime;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 동기화 이력 단일 항목 응답 DTO.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 7 — sync_logs 문서의 화면 표시 필드 매핑
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record SyncHistoryItemResponse(
    String syncId,
    String status,
    long updatedPages,
    long deletedPages,
    long duration,
    ZonedDateTime completedAt) {}
