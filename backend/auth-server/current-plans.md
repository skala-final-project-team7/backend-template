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
| **PoC** | BFF/Auth → Atlassian OAuth callback → access_token 발급 → `accessible-resources` 호출로 cloudId 조회 → **사용자 단위로 MySQL 에 암호화 저장**(access/refresh token + cloudId) → Data Ingestion(`/ml/ingest`) 호출 시 `accessToken + cloudId` 본문 직접 전달 | **3단계 (현재)** |
| 확장 | 위와 동일 저장. 단 **Data Ingestion 본문에 토큰 평문 노출 제거** → `connectionId` 발급 → Data Ingestion 에는 `connectionId + cloudId` 만 전달, Data Ingestion 이 필요 시 connectionId 로 토큰 재요청 | **3단계 이후 별도 라운드** (cutover 시점 사전 합의) |

## 외부 OAuth 계약 참조 (Atlassian 3LO)

> 출처: 팀 사전 정리 노트(AUTH-01~04) + Atlassian 공식 문서. **우리 서비스 API 가 아니라 Atlassian 제공자 엔드포인트**이며, 아래 Feature(authorize 리다이렉트·code 교환·refresh·cloudId 조회)가 호출하는 외부 계약이다. `docs/api-spec.md` §4-1 의 FE-facing 엔드포인트와 혼동 금지. 값은 초안이므로 구현 전 공식 문서로 재검증한다.
> 공식 문서: https://developer.atlassian.com/cloud/confluence/oauth-2-3lo-apps/

| #       | 엔드포인트                                                        | 용도                          | 우리 쪽 트리거                          |
| ------- | ----------------------------------------------------------------- | ----------------------------- | --------------------------------------- |
| AUTH-01 | `GET https://auth.atlassian.com/authorize`                        | 인가 화면 리다이렉트          | `GET /api/auth/login` 의 `Location`     |
| AUTH-02 | `POST https://auth.atlassian.com/oauth/token` (`authorization_code`) | code → access/refresh 교환 | `GET /api/auth/callback` 내부           |
| AUTH-03 | `POST https://auth.atlassian.com/oauth/token` (`refresh_token`)   | access token 갱신             | `POST /api/auth/refresh` 내부           |
| AUTH-04 | `GET https://api.atlassian.com/oauth/token/accessible-resources`  | 접근 가능 사이트·cloudId 조회 | callback 후 cloudId 확보                |

- **authorize(AUTH-01) 쿼리**: `audience=api.atlassian.com`, `client_id`, `scope`, `redirect_uri`, `state`(CSRF), `response_type=code`, `prompt=consent`. 콜백으로 `code`+`state` 수신.
- **토큰 교환(AUTH-02)**: body `grant_type=authorization_code` / `client_id` / `client_secret` / `code` / `redirect_uri` → 응답 `access_token` / `expires_in` / `scope`.
- **토큰 갱신(AUTH-03)**: body `grant_type=refresh_token` / `client_id` / `client_secret` / `refresh_token` → 응답에 **새 `access_token` + 새 `refresh_token`** + `expires_in` / `scope`. 실패 시 `{"error":"invalid_grant"}`.
- **cloudId(AUTH-04)**: header `Authorization: Bearer {access_token}` → 배열, 각 `{ id(=cloudId), name, url, scopes, avatarUrl }`. 이후 Confluence REST URL: `https://api.atlassian.com/ex/confluence/{cloudId}/rest/api/...`.

### 구현 시 주의 (노트 기반 — 검증 필요)

- **`offline_access` scope 필수** — refresh_token 발급 조건. authorize 의 `scope` 에 포함한다.
- **Rotating Refresh Token** — 갱신 시 기존 refresh 가 무효화되고 새 값이 발급된다 → **MySQL 암호화 저장소에 반드시 덮어쓰기**하고 이전 값은 재사용 금지.
- **scope 충분성 (※ 검증 TODO)** — 노트의 `read:confluence-content.summary` / `...-space.summary` 는 요약 수준이라 **RAG 본문·첨부 수집에 부족할 수 있다**. 실제 필요한 content/attachment 읽기 scope 를 공식 문서로 확정한다.
- `client_secret` 등은 평문 노출 금지(환경변수/Secret), 토큰 로그 마스킹(Feature D).

## 구현 Feature 및 체크리스트 (PoC)

### Feature A. Atlassian OAuth Authorization Code Flow
- [ ] `GET /api/auth/login` — Atlassian authorize 리다이렉트. **`mode=admin` 쿼리 파라미터 처리**: 값이 있으면 `state` 토큰에 함께 직렬화(서버 측 세션 또는 서명된 state)해 callback 까지 전달. 클라이언트가 callback URL 만 조작해 admin 으로 우회하는 것을 방지 (`docs/api-spec.md` §4-1, 2026-06-02 회의 결정).
- [ ] `GET /api/auth/callback` — code → access_token 교환 + **MySQL `users` upsert** (accountId 로 lookup, 없으면 `role='USER'` INSERT, 있으면 `last_login_at` 갱신). JWT `role` claim 은 `users.role` 값 사용 — config 분기 없이 DB 단일 source (`docs/db-schema.md` §6.1). **`mode=admin` 게이트**: state 에 보관된 mode 가 `admin` 인데 `users.role != ADMIN` 이면 `403 FORBIDDEN` 반환, 토큰 미발급 (`docs/api-spec.md` §4-1).
- [ ] **최초 admin seed**: 첫 배포 마이그레이션(`V001__seed_initial_admin.sql`)에 admin accountId 를 **하드코딩 INSERT** (`role='ADMIN'`). 추가 admin 권한 부여는 DB UPDATE 또는 향후 admin 관리 API.
- [ ] 토큰 발급 응답(access/refresh token + cloudId)을 **사용자 단위로 MySQL 에 암호화 저장** — admin-only ingest 흐름에서 세션·재시작 무관하게 토큰 사용 가능해야 함. 키 형식·알고리즘은 본 Feature 착수 시 확정 (`backend/rules/auth.md` §2 / `backend/auth-server/CLAUDE.md` §3.1)
- [ ] `GET /api/auth/accessible-resources` 호출로 cloudId 목록 조회
- [ ] 사용자가 선택한 cloudId 를 위 토큰 레코드와 함께 **MySQL 에 영속 저장** (`accessible-resources` 가 여러 사이트 반환 시 선택 UI 또는 단일 선택 규칙)
- [ ] `POST /api/auth/refresh` — `refresh_token` 으로 access token 재발급(AUTH-03). **Rotating Refresh**: 새 refresh 로 저장소 덮어쓰기, 만료/무효(`invalid_grant`) 시 재로그인 유도
- [ ] `POST /api/auth/logout` — refresh token 무효화 + 세션 정리. `Authorization: Bearer {accessToken}` 로 식별. 응답 `data: null` (`docs/api-spec.md` §4-1)
- [ ] `GET /api/users/me` — 현재 로그인 사용자 정보 조회(`userId`/`name`/`email`/`role`/`profileImageUrl`/`lastLoginAt`). Bearer 검증 필수, 미인증 `401(UNAUTHORIZED)` (`docs/api-spec.md` §4-1)
- [ ] **Admin Key 활성화 내부 API** — BFF 의 `POST /api/admin/key/activate`(`docs/api-spec.md` §1-4)가 호출하는 내부 endpoint. admin 의 저장 OAuth access_token 으로 Atlassian `POST /api/v2/admin-key` 호출(만료 시각 응답). admin-only ingestion 시 page-level restriction 우회를 위해 필요. ADR 0001 §2.1 참조.
- [ ] **검증 게이트 (Feature A 착수 시 첫 실행)**: 첫 admin OAuth 토큰 확보 직후 OAuth Bearer + `Atl-Confluence-With-Admin-Key: true` 헤더로 restricted page 호출 검증(curl). 200 이면 단일 OAuth 자격증명 모델 그대로 진행, 401/403/404 이면 admin API Token 별도 보관 모델로 전환(plan 한 행 정정 + Feature B 보강).

> 위 4개 호출의 외부 계약·파라미터·주의사항은 본 파일 **§외부 OAuth 계약 참조 (Atlassian 3LO)** 참고.

### Feature B. BFF 에 토큰/cloudId 전달
- [ ] `GET /api/auth/confluence-token` (내부 API) — BFF 가 현재 사용자의 `accessToken`/`cloudId` 조회
- [ ] 응답 본문에 access_token 평문 노출 — **PoC 한정**, 로그·tracing 본문 마스킹 규칙과 NetworkPolicy 로 격리 (`docs/api-spec.md` §2-1 보안 주의 참고)
- [ ] 응답에 만료 시각 포함, BFF 가 만료 임박 시 별도 갱신 호출

### Feature C. JWT 발급 계약 (확장 단계 기반 작업, 본 라운드는 인터페이스만)
- [ ] JWT Claim 셋 정의: `userId`(Confluence accountId), `groups`, `role`(`USER`/`ADMIN`, MySQL `users.role` 단일 source — `docs/db-schema.md` §6.1), `iss`, `exp`, `iat` (`backend/rules/auth.md` §2)
- [ ] 서명 알고리즘·키 형식 확정(서명 알고리즘/공개키·개인키 PEM 위치) — `backend/rules/auth.md` 참조
- [ ] 세션 JWT `Authorization: Bearer` 발급 — 로그인/갱신 응답 `data` 로 access+refresh 전달, FE 보관(HttpOnly 쿠키 미사용, `backend/rules/auth.md` §3 / `docs/api-spec.md` §4-1)
- [ ] BFF JWT 검증 필터와 계약 동기 (현재 bff-server Feature 2 가 `CurrentUserProvider` 인터페이스로 격리됨)

### Feature D. 보안 운영 규칙 (전 라운드 적용)
- [ ] 토큰 로그/tracing 본문 마스킹 (Access/Refresh 모두)
- [ ] actuator `env`/`heapdump`/`threaddump` 비노출
- [ ] Data Ingestion Pipeline Pod NetworkPolicy 적용 (호출자 화이트리스트) — `docs/api-spec.md` §2-2 보안 주의와 정합
- [ ] RabbitMQ 메시지·이벤트 페이로드에 토큰 미포함

---

## 확정된 결정

| 항목 | 결정 | 일자 |
|---|---|---|
| OAuth 도입 단계 | PoC 우선(accessToken/cloudId 직접 전달) → 후속 라운드에서 connectionId 로 확장 | 2026-05-21 |
| (예정) JWT 서명 알고리즘 | TBD (Feature C 착수 시 확정) | — |
| Confluence 토큰 저장 정책 | **PoC 부터 MySQL 암호화 저장**(access/refresh + cloudId, 사용자 단위) — admin-only ingest 가 세션 무관하게 동작하기 위해 callback 시 즉시 영속. 암호화 알고리즘·키 형식은 Feature A 착수 시 확정 | 2026-06-02 |
| admin Confluence access 패턴 | **OAuth Bearer + `Atl-Confluence-With-Admin-Key: true` 헤더**(Admin Key 활성화 후) — admin 도 일반 사용자와 동일하게 Confluence OAuth 3LO 로 로그인하며, ingestion 도 같은 OAuth 토큰 사용(별도 API Token 미보관). 사용자 → "Admin Key 발급" 버튼 → BFF `POST /api/admin/key/activate` → auth-server 가 admin OAuth 토큰으로 Atlassian 활성화. **3단계 구현 시 OAuth Bearer + Admin Key 헤더 동작 검증 게이트**. (`docs/adr/0001-page-level-acl-source.md` §2.1) | 2026-06-02 |
| role 결정 정책 | **MySQL `users.role` DB 단일 source** — OAuth callback 시 accountId lookup, 행 없으면 `role='USER'` INSERT, 행 있으면 그 값 사용. JWT `role` claim 은 그 값을 그대로 발급. 별도 YAML bootstrap config 미사용. **최초 admin** 은 첫 배포 마이그레이션(`V001__seed_initial_admin.sql`) 하드코딩 INSERT. 별도 `admins` 테이블 미사용(흡수). (`docs/db-schema.md` §6.1) | 2026-06-02 |
| (예정) Refresh Token 갱신 정책 | TBD (Feature C/확장 단계 시점) | — |
