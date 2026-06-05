# ADR 0001 — 페이지 단위 ACL 권한 데이터 소스

> 상태: **Accepted (2026-06-02)** — admin-only ingestion + Confluence Admin Key 테스트 결과(`/Users/.../confluence_admin_key_test_summary.md`, 2026-06-02) 로 구현 경로 확정. 본 ADR 의 §2.1 데이터 소스가 구체화됨.
> 일자: 2026-05-29 (초안) / 2026-06-02 (Accepted) / 2026-06-05 (Admin Key deactivate 흐름 갱신)
> 관련: `docs/architecture.md` §2·§6.3·§13, `docs/api-spec.md` §1-4·§2-1·§2-2, `backend/rules/auth.md` §2, `backend/rules/domains.md` §3, `docs/db-schema.md` §1, 기획서 §6.4/§6.6

---

## 1. 배경 (Context)

RAG 검색 파이프라인의 ACL 모델을 **스페이스 단위 → 페이지/유저 단위**로 전환하려 한다.

현재 상태:

- 질의 시점: `/ml/query` 에 JWT 의 `userId` / `groups` 를 전달해 Qdrant payload(`allowed_users` / `allowed_groups`)로 pre-filtering (`docs/api-spec.md` §2-1, 기획서 §6.4/§6.6).
- 색인 시점: RAG PoC 가 페이지 단위 권한 API 부재로 `allowed_groups = ["space:{space_key}"]` 합성, `allowed_users` 는 빈 값.
- 계획된 인가 저장소 `user_space_acl`(MySQL, 3단계, `docs/db-schema.md` §1)은 **스페이스 단위**이며, 인증(3단계)은 미착수(PoC 예정).
- "페이지 권한" 은 `backend/rules/domains.md` §3 / `backend/rules/auth.md` §2 에 언급만 있고 소스·스키마 미정의.

해결할 문제: 페이지별 접근 허용 대상(user/group)의 **source of truth** 와, 그 식별자가 질의 시점 JWT claim 과 매칭되는 체계, 갱신·예외 정책을 확정한다.

발견된 불일치(⚠️): 질의 시점 데모는 `groups = ["Cloud-Control-Center"]`(`bff-server application.yml`)를 보내는데 색인은 `allowed_groups = ["space:CPC"]` 로 합성한다. 두 표기가 달라 현재 구조로는 ACL 이 실제로 매칭되지 않는다. 본 ADR 은 **JWT claim 을 기준 vocabulary 로 고정하고 색인 측 payload 를 거기에 맞춰** 불일치를 해소한다(§2.2).

---

## 2. 결정 (Decision)

### 2.1 데이터 소스 — Confluence content restrictions API (source of truth)

페이지 단위 권한의 원천을 **Confluence** 로 둔다. 권한 정보는 **수집(Ingestion)/동기화(Sync) 단계**에서 Confluence content restrictions REST API 로 읽어 Qdrant payload(`allowed_users` / `allowed_groups`)에 적재한다.

**구체 구현 경로 (2026-06-02 Admin Key 테스트로 확정)**:

1. **수집 자격증명**: admin 의 Confluence OAuth access_token + `Atl-Confluence-With-Admin-Key: true` 헤더 (admin-only ingestion 모델). 일반 동선은 BFF `POST /api/admin/ingest` 가 auth-server 내부 API 를 통해 Admin Key 를 활성화하고, 수집 worker 는 auth-server 내부 credential 조회 API 로 admin OAuth `accessToken` + `cloudId` 를 함께 조회한다. RabbitMQ job/completion payload 에는 `accessToken`/`refreshToken`/`cloudId` 를 포함하지 않는다. 테스트 결과: page-level read restriction 을 우회해 admin 이 접근 가능한 모든 페이지 수집 가능(일반 호출 232건 → Admin Key 호출 237건, 5개 restricted page 차이 확인).
2. **본문 수집**: `GET /api/v2/pages/{id}?body-format=storage` — `body.storage.value` 가 페이지 본문(HTML/storage format), `spaceId`/`parentId`/`ownerId`/`authorId`/`version` 등 메타데이터 포함.
3. **권한 추출**: 페이지별로 `GET /rest/api/content/{pageId}/restriction/byOperation/read` 호출 → 응답의 `restrictions.user.results[].accountId` 와 `restrictions.group.results[].name` 을 추출해 Qdrant payload `allowed_users` / `allowed_groups` 로 변환.
4. **계층 권한 (open)**: 일부 페이지는 page-level restriction 이 비어 있어도 parent folder/page 또는 space permission 으로 차단된다 (테스트 §11.2). 정확한 effective ACL 산출은 §4 미해결 항목 참조.

**역할 분리**:

- BE(auth-server): admin OAuth 토큰 영속(MySQL 암호화), **Admin Key activate/deactivate 내부 API** 제공 (`POST /internal/admin/key/activate`·`/deactivate`), Data Ingestion Worker 가 `adminUserId` 로 admin OAuth `accessToken` + `cloudId` 를 함께 조회할 내부 credential API 제공. Admin Key deactivate 는 OAuth token 말소가 아니라 Atlassian Admin Key 활성 상태 말소이며, `jobId` 기준 중복 completion event 에 대해 idempotent 해야 한다.
- BFF: `POST /api/admin/key/activate` 외부 endpoint 노출(**수동/테스트용**, ADMIN 전용). **일반 동선**은 `POST /api/admin/ingest` 가 내부적으로 key 활성 미확인 시 자동 activate 후 RabbitMQ ingest job 을 발행하거나 Data Ingestion Pipeline 에 job 발행을 위임한다(2026-06-02 회의 결정의 버튼 1회 동선 유지). **2026-06-04 의 Virtual Thread watcher + `/ml/ingest/status/{jobId}` polling 방식은 2026-06-05 결정으로 RabbitMQ completion event 방식이 대체한다.** BFF consumer 가 completion event 를 consume한 뒤 auth-server deactivate 내부 API 를 호출해 Atlassian Admin Key 를 폐기한다. BFF 재시작·consumer 장애 시 RabbitMQ durable queue 의 event 재처리로 복구하며, 60분 TTL 은 최종 fallback 이다.
- ML(Data Ingestion): RabbitMQ ingest job 을 consume하고, `adminUserId` 로 auth-server 내부 credential 조회 API 에서 admin OAuth `accessToken` + `cloudId` 를 함께 조회한다. Atlassian REST 호출 시 `Authorization: Bearer {admin accessToken}` + `Atl-Confluence-With-Admin-Key: true` 헤더를 부여하고, 페이지별 restriction API 호출해 Qdrant payload 작성. 작업 완료/실패 시 RabbitMQ completion event 를 발행한다. **deactivate 직접 호출 책임 없음** — BFF consumer + auth-server deactivate 내부 API 조합으로 처리.

**RabbitMQ payload 원칙 (2026-06-05)**:

- ingest job payload: `jobId`, `adminUserId`, `mode`, `requestedAt` 등 작업 식별/상태 정보만 포함.
- completion event payload: `jobId`, `adminUserId`, `mode`, `status`, `completedAt`, `errorCode`, `message` 등 완료/실패 식별 정보만 포함.
- `accessToken`, `refreshToken`, `cloudId` 등 Confluence credential set 은 RabbitMQ payload 에 포함하지 않는다. `cloudId` 는 auth-server 내부 credential 조회 응답에서 `accessToken` 과 함께 반환된다.
- completion event 중복 수신은 정상 가능성으로 보고 `jobId` 기준 idempotent 처리한다. deactivate 실패는 재시도 후 DLQ 로 이동하며, 운영자는 원인 조치 후 event 재발행 또는 auth-server 내부 deactivate 수동 호출로 복구한다.

**검증 게이트 (3단계 구현 시)**: OAuth Bearer + Admin Key 헤더가 Atlassian 측에서 동작하는지 첫 admin OAuth 토큰 확보 직후 curl 로 검증. 실패 시 admin API Token 별도 보관 모델로 전환(plan 한 행 정정만 필요).

### 2.2 권한 데이터 형식 / 식별자 체계

**질의 시점 JWT 의 `userId`/`groups` 가 기준(canonical) vocabulary 다.** BFF/auth-server 는 JWT claim 을 변형·합성하지 않고 그대로 `/ml/query` 에 전달한다(`backend/rules/auth.md` §2: BFF 는 claim 검증만 / `docs/api-spec.md` §2-1 유지). 따라서 매칭을 맞추는 책임은 **색인(Ingestion) 측**에 있으며, Qdrant payload 의 `allowed_users`/`allowed_groups` 는 JWT 와 **동일한 vocabulary 로** 적재되어야 한다.

| 필드 | 값 체계 | 비고 |
|---|---|---|
| `allowed_users[]` | JWT `sub`(=`userId`) 와 동일 식별자 | 페이지에 명시적 user restriction 이 있을 때 채움 (현행 빈 값 → 실제 사용자 식별자) |
| `allowed_groups[]` | JWT `groups` 와 동일 식별자 | content restriction 의 group 을 JWT vocabulary 로 표기 |

- JWT `userId`/`groups` 가 실제로 어떤 값 체계인지(예: Confluence accountId / group name / groupId)는 **auth-server 의 Confluence OAuth 매핑이 결정**한다 → §4 확인. 색인은 그 체계를 그대로 따른다.
- 과거 PoC 의 합성 토큰 `space:{spaceKey}` 모델은 폐기(2026-06-04, api-spec v2.4.0 spaceKey 제거 결정과 함께). 색인은 Confluence content restrictions API 응답의 **실제 group 식별자**(`restrictions.group.results[].name`)를 그대로 `allowed_groups` 에 적재한다 — JWT vocabulary 와 동일 체계(§4.1 확인 항목).

### 2.3 질의 시점 매칭 (JWT ↔ payload)

- JWT `sub`(=`userId`) / `groups` 를 **변형 없이 그대로** 매칭에 사용한다(pass-through).
- 가시성 판정(합집합): `allowed_users ∋ userId` **OR** `allowed_groups ∩ groups ≠ ∅`.
- 2단계 데모 고정값(`fixed-user-id`/`fixed-groups`)도 JWT 가 실을 vocabulary 와 동일하게 맞춘다. §1 불일치는 JWT 를 바꾸는 대신 **색인 payload 를 JWT 에 맞춰** 해소한다.
- **표기 주의:** 여기서 pass-through 는 식별자 *값*의 무변형(합성·변환 없음)을 뜻한다. JWT claim 과 `/ml/query` 와이어 필드명을 **모두 camelCase `userId`/`groups`** 로 통일한다(`docs/api-spec.md` §2-1, `backend/rules/auth.md` §2). Qdrant payload 키(`allowed_users`/`allowed_groups`)는 ML/Qdrant 측 표기를 따른다.

### 2.4 갱신 · 동기화

- 권한 변경은 본문 변경과 독립적으로 발생하므로(내용 동일, restriction 만 변경) 본문 해시 기반 델타로는 누락된다.
- **PoC 정책:** 전체/델타 색인 시 restriction 을 함께 재조회해 payload ACL 을 upsert 한다(재임베딩 없이 payload 만 갱신 가능). 변경 건수는 `sync_logs` 에 기록.
- 실시간 변경 통지(Confluence webhook 등)는 **후속 과제**로 보류한다.

### 2.5 권한 부재 · 예외 처리 (fail-closed)

`docs/architecture.md` §13(권한 필터 없이 결과 제공 금지) 원칙을 따른다. 단 두 경우를 구분한다.

| 상황 | 정책 |
|---|---|
| 명시적 restriction 없음 (= 스페이스 상속/스페이스 멤버 공개) | 해당 스페이스 read 권한 group 을 **JWT vocabulary 와 동일한 식별자**로 `allowed_groups` 에 적재 (공개=스페이스 멤버 취급). 과거 PoC 가 사용하던 `space:{spaceKey}` 합성 토큰 모델은 v2.4.0(2026-06-04) `spaceKey` 전면 제거와 함께 폐기 — 색인은 Confluence content restrictions 기반 실제 group 식별자만 적재 |
| effective 권한 산출 실패 (API 오류·타임아웃) | **해당 페이지 색인 보류(차단)**, `sync_logs` 실패 기록 후 재시도 — "불확실하면 차단" |
| 질의 시점 `groups`/`userId` 부재 | 질의 거부 (ACL 누락 호출 금지, `backend/CLAUDE.md` §6) |

---

## 3. 영향 범위 (Consequences)

- **BE(auth-server):** JWT `userId`/`groups` vocabulary 를 확정해 공개(3단계 Feature C). claim 변형·합성 책임 없음 — 로그인 시 Confluence OAuth 에서 산출한 값을 그대로 발급.
- **BE(bff-server):** JWT claim 을 변형 없이 `/ml/query` 로 pass-through(현행 유지). 데모 `fixed-user-id`/`fixed-groups` 를 확정된 JWT vocabulary 에 맞춤.
- **RAG/Ingestion:** content restrictions 조회·상속(effective) 산출 후, **JWT vocabulary 와 동일한 식별자**로 payload(`allowed_users`/`allowed_groups`) 적재. inherited/실패 케이스 처리.
- **문서:** 합의 후 `docs/api-spec.md` §2-1/§2-2, `docs/architecture.md` §6.3, `backend/rules/auth.md`·`domains.md` 갱신.

---

## 4. 미해결 / 확인 필요 (Open Questions)

1. **JWT vocabulary 확정** — `userId` 는 **Confluence `accountId`** 로 확정(`docs/db-schema.md` §6.1 `users.user_id` 정의, 2026-06-02). `groups` vocabulary 는 group `name` (restriction API 응답 형식) 으로 정합 가능성 크나, auth-server `/api/users/me` 또는 Confluence 그룹 API 첫 호출 결과로 최종 확정 필요.
2. **inherited 권한 산출 책임** — Admin Key 테스트(§11.2) 에서 page-level restriction 비어 있어도 일반 호출에서 안 보이는 페이지 2건 확인. parent folder/page restriction 또는 space permission 계층 영향. PoC 결정 필요: (a) page-level restriction 만 반영하고 누락 감수 vs (b) 상위 계층까지 조회해 effective 산출. (a) 가 단순하지만 일부 페이지 권한 누설 위험.
3. 첨부파일(`raw_attachments`) 권한이 부모 페이지 권한을 따르는지 — 테스트 미실시. 첨부 API 응답에 별도 restriction 이 있는지 확인 필요.
4. `/ml/query` 가 실시간 Confluence 호출을 일절 하지 않는다는 전제(§2-1) 재확인 — 본 ADR 도 이 전제에 의존.
5. **OAuth Bearer + Admin Key 헤더 동작 검증** (구현 게이트) — 팀 MD 테스트는 API Token (Basic auth) 으로만 검증. admin OAuth Bearer 와 Admin Key 헤더 조합이 Atlassian 측에서 동일하게 동작하는지 3단계 구현 시 첫 검증. 실패 시 admin API Token 별도 보관 모델로 전환(plan 한 행 정정).
6. ~~scope 충분성~~ (이전 미해결) — Admin Key 가 admin 권한 그대로 부여하므로 OAuth scope 충분성 검증 불필요(2026-06-02 해소).
