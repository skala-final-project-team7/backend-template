# backend/bff-server/CLAUDE.md

이 문서는 `bff-server` 모듈에서 Claude Code가 따라야 하는 모듈 전용 규칙을 정의한다.
루트 `CLAUDE.md`, `backend/CLAUDE.md`, `docs/architecture.md`, `docs/conventions.md` 의 공통 규칙을 우선 적용하고, 이 문서는 bff-server 고유 규칙만 다룬다.

> 이 문서에서 언급하는 `CLAUDE.md`, `docs/...`, `backend/...` 경로는 모두 **프로젝트 루트** 기준이다.

---

## 1. 모듈 역할

- API 단일 진입점 (Spring Cloud Gateway Server MVC + Spring MVC + Virtual Threads)
- Authorization Server가 발급한 JWT의 서명/만료/Claim 검증 (3단계 이후)
- 라우팅, Rate Limiting, CORS 처리, 내부 API 결과 조합
- RAG Pipeline HTTP 호출 및 SSE 스트리밍 중계
- 대화/메시지/피드백(MongoDB) CRUD

세부 계층 구조와 책임은 `docs/architecture.md` §7, `docs/conventions.md` §6, `backend/CLAUDE.md` §2/§3 을 따른다.

---

## 2. 작업 진행 절차

- 작업 착수 전 `backend/bff-server/current-plans.md` 의 Feature 목록을 확인한다.
- Feature 단위로 구현하며, 완료된 항목은 해당 파일 체크박스를 `[x]` 로 변경한다.
- 다음 Feature 가 다른 책임 영역에 속하면 새 세션에서 시작한다.

---

## 3. bff-server 고유 규칙

### 3.1 Virtual Threads / 동기 I/O
- `WebFlux`, `Mono`, `Flux` 사용 금지 (`backend/CLAUDE.md` §2.1 / §6 위반).
- SSE 중계는 `SseEmitter` + 동기 `RestClient` / `HttpClient` InputStream 파싱만 사용한다.
- I/O 블로킹은 Virtual Thread 에 위임한다(`spring.threads.virtual.enabled: true`).

### 3.2 RAG Pipeline 호출 경계
- ML 서버 호출 코드는 `rag/client/` 패키지에서만 작성한다. Service 계층의 직접 HTTP 호출 금지.
- ACL(`userId`, `groups`) 누락 호출 경로를 만들지 않는다 (`backend/rules/rag-pipeline.md` §2).
- 실패/타임아웃 시 재시도 없이 `error` 이벤트 전송 후 연결을 정리한다.

### 3.3 인증 (2단계 한정 데모 처리)
- production Controller/Service 에 인증 비활성화 조건 분기를 추가하지 않는다.
- 데모용 `DemoSecurityConfig`(`permitAll`)와 `CurrentUserProvider` 추상화로 경계를 격리한다 (3단계에서 JWT 구현체로 교체).

### 3.4 영속 (MongoDB)
- `conversations`, `messages`(`sources` 내장), `feedbacks` 만 CRUD 한다.
- RAG 파이프라인 데이터(`raw_pages`, `chunked_units` 등)에 쓰기 금지 (`backend/CLAUDE.md` §6).
- 모든 조회는 `deletedAt == null` 필터를 적용한다(soft delete).

---

## 4. 작업별 참조 규칙 파일

| 작업 유형 | 참조 파일 |
|---|---|
| 도메인 (chat/feedback) | `backend/rules/domains.md` |
| RAG Pipeline 연동 | `backend/rules/rag-pipeline.md` |
| 인증/인가 (3단계 이후) | `backend/rules/auth.md` |
| 테스트 작성 | `backend/rules/testing.md` |

---

## 5. 모듈 체크리스트

- [ ] `Mono`/`Flux`/WebFlux 미사용
- [ ] ACL 누락 RAG 호출 경로 없음
- [ ] SSE 연결 타임아웃 및 error 이벤트 경로 구현
- [ ] MongoDB RAG 파이프라인 데이터 쓰기 없음
- [ ] 평문 secret 미포함 (DB URI/비밀번호 등 환경변수 참조)
