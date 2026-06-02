# Backend - RAG Pipeline 연동 규칙

`backend/CLAUDE.md`와 함께 적용한다. ML 서버 호출, SSE 중계 등 RAG Pipeline 연동 작업 시 이 문서를 참조한다.

> 이 문서에서 언급하는 `CLAUDE.md`, `docs/...` 경로는 모두 **프로젝트 루트** 기준이다.

---

## 1. 호출 구조

```text
BFF Server → (HTTP/SSE) → FastAPI ML Server
```

- RAG Pipeline 호출은 `rag/client/` 패키지의 Client 계층에서 수행한다.
- Service 계층에서 직접 HTTP 호출을 하지 않는다.

---

## 2. 요청 시 전달 항목

RAG Pipeline 호출 시 다음 정보를 전달한다 (상세 계약: `docs/api-spec.md` §2-1).

- 사용자 질문 텍스트 (`question`)
- 대화 이력 (`history`, 최근 N턴) — `history[].role` 은 lowercase(`user`/`assistant`, LLM/OpenAI 표준, 저장값과 동일)
- ACL 필터 정보 (`userId`, `groups`) — 빈 값이면 BFF 가 호출을 **차단**(fail-closed)
- 대화방 ID (`conversationId`)
- 검색 스코프 (`spaceKey`) — **선택**. 누락 시 사용자 접근 가능 모든 스페이스 cross-space 검색(`userId`/`groups` ACL 만 적용), 지정 시 해당 스페이스로 좁힘. ACL 이 아닌 스코프 필드이므로 fail-closed 대상 아님 (2026-06-02 결정, `docs/api-spec.md` §2-1)
- `stream: true` — 토큰 스트리밍 명시 (BFF 는 항상 `true` 로 호출)

---

## 3. 응답 처리

- ML 서버의 SSE 스트림을 수신하여 프론트엔드로 중계한다.
- user 메시지는 질의 시작 시 **선저장**, assistant 메시지(+`sources`+`verification`)는 **`done` 수신 시** MongoDB `messages` 컬렉션(`docs/db-schema.md` §3.2)에 저장한다. `error` 종료 시 assistant 는 미저장 (`docs/api-spec.md` §1-1 "스트림 종료·영속 규칙").
- **Boundary 가공 (RAG → BFF → FE)**:
  - `error` 이벤트: RAG·BFF·FE 모두 `{ "errorCode": ..., "message": ... }` 동일 키 — passthrough, `errorCode` 값이 SSE 에러 코드 enum 표(`docs/api-spec.md` §1-1)와 일치하는지만 검증
  - `done` 이벤트: RAG `{}` → BFF 가 저장한 assistant 의 `messageId` 를 채워 FE 로 `done: { "messageId": ... }` 중계 (가공 필요)
- ML 서버 호출 실패 시 재시도하지 않고, 에러 이벤트를 프론트엔드에 전달한다.
- ML 서버 응답 타임아웃은 설정값으로 관리한다 (§4 SSE 스트리밍 규칙 참조).

---

## 4. SSE 스트리밍 규칙

ML Pipeline의 응답을 프론트엔드로 중계할 때 SSE(Server-Sent Events) 스트리밍을 사용한다.

- 반환 타입은 `SseEmitter`를 사용한다.
- SSE 타임아웃은 **idle 기준**으로 설정한다(기본 60초, `lina.rag.sse-timeout-ms`). 장시간 phase 동안 `status` 이벤트로 keep-alive 하고, idle 초과 시 `error`(`ML_TIMEOUT`) 전송 후 정리한다 (계약: `docs/api-spec.md` §1-1).
- SSE 이벤트 타입(집합 정본은 `docs/api-spec.md` §1-1): `status`(진행 상태), `token`(답변 토큰), `sources`(인용 출처), `verification`(검증 결과), `meta`(현재 ML 구현 호환용·제거 예정), `done`(완료·`messageId`), `error`(오류 종료·`errorCode`). 스트림은 `done` / `error` 중 하나로 종료한다.
- 연결 실패, ML Pipeline 타임아웃 시 적절한 에러 이벤트를 전송하고 연결을 정리한다.
