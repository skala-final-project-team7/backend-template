package com.lina.bff.rag.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : RAG Pipeline POST /ml/query 의 출력인 SSE event/data 쌍을 표현하는 중계 DTO.
 *           status/token/sources/verification/meta/done/error 이벤트 이름과 JSON payload 를 보존한다.
 *           ChatService 가 done/error boundary 가공 전 원본 이벤트를 순서대로 소비한다.
 *           event 는 SSE event 이름이고, data 는 해당 이벤트의 JSON payload 이다.
 * 작성일 : 2026-06-05
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-05, 최초 작성, 2단계 Feature 5 SSE 파싱 결과 모델 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Jackson JsonNode 기반 payload 보존
 * --------------------------------------------------
 * </pre>
 */
public record RagSseEvent(String event, JsonNode data) {}
