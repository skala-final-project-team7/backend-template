package com.lina.bff.admin.dashboard.dto;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 기간 필터. 입력/응답 기준 KST와 조회 기준 UTC Instant 를 함께 보관한다.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 2 — KST 입력 → UTC 조회 범위 변환 모델 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 * --------------------------------------------------
 * </pre>
 */
public record AdminDashboardTimeRange(
    ZonedDateTime fromKst, ZonedDateTime toKst, Instant fromUtc, Instant toUtc) {}
