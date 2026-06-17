package com.lina.bff.admin.dto;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 수집 진행상태 응답(GET /api/admin/ingest/status/{jobId}) — Data Ingestion
 *           Pipeline 의 GET /ml/ingest/status/{jobId} 응답을 그대로 중계한다(api-spec §2-3).
 *           필드는 FE AdminIngestStatusResponse 계약과 동일(camelCase).
 * --------------------------------------------------
 * </pre>
 */
public record AdminIngestStatusResponse(
    String jobId,
    String status,
    Integer totalPages,
    Integer processedPages,
    Integer failedPages,
    String startedAt) {}
