package com.lina.bff.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : FE 가 챗봇 질의 SSE 엔드포인트로 전달하는 요청 DTO.
 *           질문 본문만 받고 대화 ID 는 path variable 로 분리한다.
 * 작성일 : 2026-06-07
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-07, 최초 작성, 2단계 Feature 5 챗 SSE 요청 DTO 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Jakarta Validation 기준
 * --------------------------------------------------
 * </pre>
 */
public record ChatRequest(@NotBlank String question) {}
