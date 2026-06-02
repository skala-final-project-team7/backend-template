# backend/CLAUDE.md

이 문서는 LINA 프로젝트의 Backend 영역에서 Claude Code가 따라야 하는 규칙을 정의한다.  
루트 `CLAUDE.md`, `docs/architecture.md`, `docs/conventions.md`의 공통 규칙을 우선 적용하고, 이 문서는 Backend 고유 규칙만 다룬다.

> 이 문서에서 언급하는 `CLAUDE.md`, `docs/...` 경로는 모두 **프로젝트 루트** 기준이다.

이 문서는 항상 적용되는 cross-cutting 규칙만 포함한다. 작업 유형별 상세 규칙은 `backend/rules/` 아래 분리되어 있으며, 아래 **§3 작업별 참조 규칙 파일** 표에 따라 해당 작업 시 함께 읽고 따른다.

**작업을 시작하기 전, §3 표에서 해당 작업 유형을 확인하고 매칭되는 sub-file을 먼저 Read한 뒤 구현에 들어간다.** 작업이 여러 유형에 걸치면 해당 파일을 모두 읽는다.

---

## 1. Backend 개요

Backend는 BFF Server와 Authorization Server로 구성된다.

| 컴포넌트 | 기술 | 역할 |
|---|---|---|
| **BFF Server** | Spring Cloud Gateway Server MVC + Spring Boot 3.x + Java 21 + Virtual Threads | API 단일 진입점, Authorization Server가 발급한 JWT의 서명/만료/권한 Claim 검증, 서비스별 라우팅, Rate Limiting, CORS 처리, 내부 API 결과 조합, RAG Pipeline 호출, SSE 스트리밍 중계, 메타데이터 처리, 피드백 상태 관리 |
| **Authorization Server** | Spring Security OAuth2 Client | Confluence OAuth 2.0 인증/인가, Access/Refresh Token 관리 및 갱신, 사용자 스페이스 접근 권한(ACL) 조회, JWT 발급 |

계층 구조, 패키지 의존 방향, Layer 책임은 `docs/architecture.md` 7장과 `docs/conventions.md` 6장을 따른다.

---

## 2. 기술 제약

### 2.1 Virtual Threads 규칙

BFF Server는 I/O 대기 비중이 높은 구조(RAG 서버 호출, DB 접근, 외부 API 호출, SSE 응답 중계)이므로 Spring MVC + Virtual Threads 조합을 사용한다.

- WebFlux, Reactive Stream 기반 코드를 작성하지 않는다.
- `Mono`, `Flux` 타입을 사용하지 않는다.
- 동기식 코드의 단순성을 유지하면서 Virtual Threads가 I/O 블로킹을 처리하도록 한다.
- `synchronized` 블록 내에서 I/O 작업을 수행하지 않는다 (Virtual Thread pinning 방지).
- `ThreadLocal` 사용 시 Virtual Thread 환경에서의 동작을 검증한다.

### 2.2 데이터베이스

- MySQL과 MongoDB의 역할 분리 및 저장 대상은 `docs/architecture.md`, `docs/db-schema.md`를 따른다.
- Backend는 MongoDB의 대화/피드백 컬렉션(`conversations`, `messages`, `feedbacks`)에 CRUD를 수행한다. RAG 파이프라인 데이터(`raw_pages`, `raw_attachments`, `attachment_texts`, `chunked_units`, `import_jobs`, `sync_logs` 등)는 조회만 수행하며, 쓰기는 Ingestion/Sync Worker가 담당한다.
- OAuth 토큰은 MySQL에 암호화 저장한다(3단계). 평문 저장 금지. (2단계에서는 MySQL 미사용 — `users`(`role` 컬럼 포함)/`user_tokens`/`user_space_acl`는 3단계에서 도입. 별도 `admins` 테이블은 `users.role` 컬럼으로 흡수 — 2026-06-02 결정, `docs/db-schema.md` §6.1.)

### 2.3 메시지 큐

- **RabbitMQ** — Confluence 데이터 적재/청킹/임베딩 비동기 파이프라인
- Backend에서 RabbitMQ에 직접 메시지를 발행하는 경우는 제한적이다. 대부분의 메시지 발행은 Ingestion/Sync Worker가 담당한다.

---

## 3. 작업별 참조 규칙 파일

아래 작업을 수행할 때는 해당 파일을 **먼저 Read한 뒤** 작업을 시작한다. 메인 파일(`backend/CLAUDE.md`)의 cross-cutting 규칙은 모든 작업에 항상 적용된다.

| 작업 유형 | 참조 파일 |
|---|---|
| 인증/인가 (Confluence OAuth 2.0 흐름, JWT 검증, 토큰 저장/갱신) | `backend/rules/auth.md` |
| 도메인 구현 (chat, feedback, user, admin) | `backend/rules/domains.md` |
| RAG Pipeline 연동 (ML 서버 HTTP/SSE 호출, 응답 중계, ACL 전달) | `backend/rules/rag-pipeline.md` |
| 테스트 작성 (Unit, MockMvc, DataJpaTest, WireMock, SecurityTest) | `backend/rules/testing.md` |

---

## 4. 예외 처리 규칙

공통 예외 규칙은 `docs/conventions.md` 6.3장을 따른다. Backend 고유 예외를 다음과 같이 분류한다.

| 예외 유형 | HTTP Status | 예시 |
|---|---|---|
| 인증 실패 | 401 | JWT 만료, 유효하지 않은 토큰 |
| 권한 없음 | 403 | 관리자 API에 일반 사용자 접근 |
| 리소스 없음 | 404 | 존재하지 않는 대화방, 메시지 |
| 입력값 오류 | 400 | 필수 필드 누락, 형식 오류 |
| 외부 서비스 실패 | 502 | ML Pipeline 호출 실패, Confluence API 실패 |
| 내부 서버 오류 | 500 | 예상하지 못한 시스템 오류 |

---

## 5. 설정 관리 규칙

- 환경별 설정은 `application-{profile}.yml`로 분리한다.
- Secret, Token, Credential은 환경 변수 또는 Kubernetes Secret으로 주입한다. `application.yml`에 평문으로 작성하지 않는다.
- ML Pipeline URL, Confluence API URL, DB 접속 정보 등 외부 서비스 주소는 설정값으로 관리한다.
- 타임아웃, 재시도 횟수, 대화 이력 전달 턴 수 등 운영 파라미터는 설정값으로 관리한다.

---

## 6. Backend 금지 사항

공통 금지 사항은 `CLAUDE.md`를 따른다. Backend 고유 금지 사항은 다음과 같다.

- WebFlux, Reactive Stream(`Mono`, `Flux`)을 사용하지 않는다.
- MongoDB의 RAG 파이프라인 데이터(`raw_pages`, `raw_attachments`, `attachment_texts`, `chunked_units`, `import_jobs`, `sync_logs` 등)에 쓰기 작업을 수행하지 않는다 (해당 컬렉션은 Backend가 읽기 전용). 대화/피드백 컬렉션(`conversations`, `messages`, `feedbacks`)은 CRUD 가능.
- BFF Server에서 Confluence 토큰을 직접 교환하지 않는다 (Authorization Server 위임).
- ACL 필터 없이 RAG Pipeline을 호출하지 않는다.

---

## 7. Backend 작업 체크리스트

공통 체크리스트는 `CLAUDE.md`를 따른다. Backend 고유 확인 항목은 다음과 같다.

- [ ] ACL 필터 없이 RAG Pipeline을 호출하는 경로가 없는가
- [ ] SSE 스트리밍 연결의 타임아웃 및 에러 처리가 구현되었는가
- [ ] MongoDB의 RAG 파이프라인 데이터(`raw_pages`/`chunked_units` 등)에 쓰기 작업이 포함되지 않았는가
- [ ] BFF Server에서 Confluence 토큰을 직접 교환하는 코드가 없는가
- [ ] OAuth 토큰이 평문으로 저장되지 않았는가
- [ ] `Mono`, `Flux` 타입이 사용되지 않았는가
