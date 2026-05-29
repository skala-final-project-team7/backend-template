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

RAG Pipeline 호출 시 다음 정보를 전달한다.

- 사용자 질문 텍스트
- 대화 이력 (최근 N턴)
- ACL 필터 정보 (`userId`, `groups`)
- 대화방 ID

---

## 3. 응답 처리

- ML 서버의 SSE 스트림을 수신하여 프론트엔드로 중계한다.
- 답변 완료 후 메시지(질문 + 답변 + 인용 출처 + 검증 결과)를 MySQL에 저장한다.
- ML 서버 호출 실패 시 재시도하지 않고, 에러 이벤트를 프론트엔드에 전달한다.
- ML 서버 응답 타임아웃은 설정값으로 관리한다.

---

## 4. SSE 스트리밍 규칙

ML Pipeline의 응답을 프론트엔드로 중계할 때 SSE(Server-Sent Events) 스트리밍을 사용한다.

- 반환 타입은 `SseEmitter`를 사용한다.
- SSE 연결 타임아웃을 설정한다 (기본 60초).
- SSE 이벤트 타입을 구분한다: `token`(답변 토큰), `sources`(인용 출처), `verification`(검증 결과), `done`(완료), `error`(ML 서버 오류).
- 연결 실패, ML Pipeline 타임아웃 시 적절한 에러 이벤트를 전송하고 연결을 정리한다.
