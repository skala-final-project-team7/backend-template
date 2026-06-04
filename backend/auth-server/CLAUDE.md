# backend/auth-server/CLAUDE.md

이 문서는 `auth-server` 모듈에서 Claude Code가 따라야 하는 모듈 전용 규칙을 정의한다.
루트 `CLAUDE.md`, `backend/CLAUDE.md`, `backend/rules/auth.md` 의 공통 규칙을 우선 적용하고, 이 문서는 auth-server 고유 규칙만 다룬다.

> 이 문서에서 언급하는 `CLAUDE.md`, `docs/...`, `backend/...` 경로는 모두 **프로젝트 루트** 기준이다.

---

## 1. 모듈 역할

- Confluence OAuth 2.0 Authorization Code Flow 처리
- Access/Refresh Token MySQL **암호화 저장** (평문 저장 금지)
- 사용자 스페이스 접근 권한(ACL) 조회·관리
- JWT 발급 (Claim: `userId`, `groups`, `role`, `iss`, `exp`, `iat`) — `role` 은 MySQL `users.role` 단일 source, `userId` 는 Confluence accountId (`docs/db-schema.md` §6.1)
- Refresh Token 기반 자동 갱신

세부 흐름은 `docs/architecture.md` §7, `backend/rules/auth.md` §1·§2 를 따른다.

---

## 2. 작업 진행 절차

- 작업 착수 전 `backend/auth-server/current-plans.md` 의 Feature 목록을 확인한다.
- Feature 단위로 구현하며, 완료된 항목은 해당 파일 체크박스를 `[x]` 로 변경한다.

---

## 3. auth-server 고유 규칙

### 3.1 토큰 보안
- Access/Refresh Token 은 MySQL 에 **암호화 저장**한다. 평문 저장 금지.
- **Confluence OAuth Token(Atlassian)은 프론트엔드에 노출하지 않는다**(서버 MySQL 보관). LINA 세션 JWT 는 `Authorization: Bearer` 방식으로 발급한다(HttpOnly 쿠키 미사용, `docs/api-spec.md` §4-1).
- 로그에 Token / Secret 원문을 남기지 않는다 (`docs/conventions.md` §6.4).

### 3.2 JWT 발급 계약
- 서명 알고리즘과 키 형식은 `backend/rules/auth.md` 와 일치시킨다.
- BFF Server 의 JWT 검증 필터와 동일한 Claim 셋을 사용한다.
- 토큰 만료 시각/issuer 등 메타데이터는 운영 파라미터로 외부화한다.

### 3.3 OAuth 위임
- BFF Server 가 Confluence 토큰을 직접 교환하지 않도록 모든 교환을 auth-server 가 담당한다.
- Confluence API 호출 시 OAuth Token 유효성을 사전에 확인하고, 만료 시 Refresh 흐름을 우선 시도한다.

---

## 4. 작업별 참조 규칙 파일

| 작업 유형 | 참조 파일 |
|---|---|
| 인증/인가 흐름 전반 | `backend/rules/auth.md` |
| 테스트 작성 | `backend/rules/testing.md` |

---

## 5. 모듈 체크리스트

- [ ] OAuth Token 평문 저장 없음 (암호화 컬럼 + KMS/Vault 키 사용)
- [ ] Confluence OAuth Token 이 FE 응답에 포함되지 않음(서버 보관). 세션 JWT 는 `Authorization: Bearer` 발급(쿠키 미사용)
- [ ] JWT Claim 셋이 BFF 검증 로직과 일치
- [ ] 평문 secret 미포함 (OAuth client-id/secret 환경변수 참조)
- [ ] 인증 흐름 우회 코드 없음 (테스트는 Test Security Config 사용)
