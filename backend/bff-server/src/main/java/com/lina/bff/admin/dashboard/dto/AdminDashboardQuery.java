package com.lina.bff.admin.dashboard.dto;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 API 공통 query parameter 파싱 결과.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 2 — period/from/to/page/size 공통 모델 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record AdminDashboardQuery(
    AdminDashboardPeriod period,
    AdminDashboardTimeRange timeRange,
    AdminDashboardPageRequest pageRequest) {}
