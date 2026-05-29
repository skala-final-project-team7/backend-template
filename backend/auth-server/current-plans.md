# auth-server Current Plans

> `auth-server` 모듈의 현재 진행 중인 Plan. 세션 간 인수인계용 (`docs/ai/workflow.md` §6.3).
> 모듈 전용 규칙은 `backend/auth-server/CLAUDE.md` 참조.

---

## 진행 상태 요약

| 단계 | 범위 | 상태 |
|---|---|---|
| 1단계 | 프로젝트 초기 셋업 (패키지 구조, 설정, 공통 응답/예외) | ✅ 완료 (2026-05-15, `backend/bff-server/current-plans.md` 1단계 공동 작업) |
| 3단계 | Confluence OAuth 2.0 Authorization Code Flow + Access/Refresh Token 암호화 저장 + JWT 발급 | 📝 미착수 |

---

# 3단계 — Authorization Server

> 착수 전 상세 Plan 을 본 파일에 추가한다. 참조: `backend/rules/auth.md`, `docs/api-spec.md` §4-1, `docs/api-spec.md` §2-1(PoC 토큰 전달).

## 작업 요약 (PoC 모드 우선, 2026-05-21 확정)

3단계는 두 모드를 단계적으로 도입한다. **이번 라운드는 PoC 모드만 구현**한다.

| 모드 | 흐름 | 적용 시점 |
|---|---|---|
| **PoC** | BFF/Auth → Atlassian OAuth callback → access_token 발급 → `accessible-resources` 호출로 cloudId 조회 → 사용자가 선택한 cloudId 저장 → AI Agent 호출 시 `accessToken + cloudId` 본문 직접 전달 | **3단계 (현재)** |
| 확장 | 위 흐름에서 OAuth 연동 정보를 DB 에 암호화 저장 → `connectionId` 발급 → AI Agent 에는 `connectionId + cloudId` 만 전달, AI Agent 가 필요 시 connectionId 로 토큰 재요청 | **3단계 이후 별도 라운드** (cutover 시점 사전 합의) |

## 구현 Feature 및 체크리스트 (PoC)

### Feature A. Atlassian OAuth Authorization Code Flow
- [ ] `GET /api/auth/login` — Atlassian authorize 리다이렉트 (`state` 발급·검증)
- [ ] `GET /api/auth/callback` — code → access_token 교환
- [ ] 토큰 발급 응답을 메모리/세션 스토어에 보관 (PoC: 영속 미사용, 확장 단계에서 MySQL 암호화 저장으로 전환)
- [ ] `GET /api/auth/accessible-resources` 호출로 cloudId 목록 조회
- [ ] 사용자가 선택한 cloudId 저장 (세션 또는 임시 스토어)

### Feature B. BFF 에 토큰/cloudId 전달
- [ ] `GET /api/auth/confluence-token` (내부 API) — BFF 가 현재 사용자의 `accessToken`/`cloudId` 조회
- [ ] 응답 본문에 access_token 평문 노출 — **PoC 한정**, 로그·tracing 본문 마스킹 규칙과 NetworkPolicy 로 격리 (`docs/api-spec.md` §2-1 보안 주의 참고)
- [ ] 응답에 만료 시각 포함, BFF 가 만료 임박 시 별도 갱신 호출

### Feature C. JWT 발급 계약 (확장 단계 기반 작업, 본 라운드는 인터페이스만)
- [ ] JWT Claim 셋 정의: `userId`, `groups`, `iss`, `exp`, `iat` (`backend/rules/auth.md` §2)
- [ ] 서명 알고리즘·키 형식 확정(서명 알고리즘/공개키·개인키 PEM 위치) — `backend/rules/auth.md` 참조
- [ ] HttpOnly Cookie 발급 방식 (`backend/rules/auth.md` §3, Body 금지)
- [ ] BFF JWT 검증 필터와 계약 동기 (현재 bff-server Feature 2 가 `CurrentUserProvider` 인터페이스로 격리됨)

### Feature D. 보안 운영 규칙 (전 라운드 적용)
- [ ] 토큰 로그/tracing 본문 마스킹 (Access/Refresh 모두)
- [ ] actuator `env`/`heapdump`/`threaddump` 비노출
- [ ] AI Agent Pod NetworkPolicy 적용 (호출자 화이트리스트)
- [ ] RabbitMQ 메시지·이벤트 페이로드에 토큰 미포함

---

## 확정된 결정

| 항목 | 결정 | 일자 |
|---|---|---|
| OAuth 도입 단계 | PoC 우선(accessToken/cloudId 직접 전달) → 후속 라운드에서 connectionId 로 확장 | 2026-05-21 |
| (예정) JWT 서명 알고리즘 | TBD (Feature C 착수 시 확정) | — |
| (예정) Token 암호화 방식 | TBD (확장 단계 — DB 저장 도입 시 확정) | — |
| (예정) Refresh Token 갱신 정책 | TBD (Feature C/확장 단계 시점) | — |
