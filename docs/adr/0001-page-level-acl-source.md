# ADR 0001 — 페이지 단위 ACL 권한 데이터 소스

> 상태: **Proposed (협의 중)** — RAG/ML 팀 합의 후 Accepted 로 전환하고 `docs/api-spec.md`·`docs/architecture.md` 본문을 갱신한다.
> 일자: 2026-05-29
> 관련: `docs/architecture.md` §2·§6.3·§13, `docs/api-spec.md` §2-1·§2-2, `backend/rules/auth.md` §2, `backend/rules/domains.md` §3, `docs/db-schema.md` §1, 기획서 §6.4/§6.6

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

- 읽기 권한 조회 엔드포인트(예): `GET /wiki/rest/api/content/{id}/restriction/byOperation/read` — 해당 페이지의 read restriction(users/groups) 반환.
- 권한 적재 주체는 RAG 측 Ingestion/Sync Worker. **BE(auth-server)의 역할은 (a) 수집 단계 Confluence 호출용 OAuth 토큰 제공, (b) 질의 시점 JWT 에 일관된 식별자 체계로 `userId`/`groups` claim 발급** 두 가지로 한정한다. BE 는 별도 페이지 권한 테이블을 운영하지 않는다(이중 관리 회피).
- 토큰 전달 경로는 기존 결정 유지: `/ml/ingest` 본문의 `accessToken`/`cloudId`(PoC) → 후속 `connectionId`(`docs/api-spec.md` §2-2).

### 2.2 권한 데이터 형식 / 식별자 체계

**질의 시점 JWT 의 `userId`/`groups` 가 기준(canonical) vocabulary 다.** BFF/auth-server 는 JWT claim 을 변형·합성하지 않고 그대로 `/ml/query` 에 전달한다(`backend/rules/auth.md` §2: BFF 는 claim 검증만 / `docs/api-spec.md` §2-1 유지). 따라서 매칭을 맞추는 책임은 **색인(Ingestion) 측**에 있으며, Qdrant payload 의 `allowed_users`/`allowed_groups` 는 JWT 와 **동일한 vocabulary 로** 적재되어야 한다.

| 필드 | 값 체계 | 비고 |
|---|---|---|
| `allowed_users[]` | JWT `sub`(=`userId`) 와 동일 식별자 | 페이지에 명시적 user restriction 이 있을 때 채움 (현행 빈 값 → 실제 사용자 식별자) |
| `allowed_groups[]` | JWT `groups` 와 동일 식별자 | content restriction 의 group 을 JWT vocabulary 로 표기 |

- JWT `userId`/`groups` 가 실제로 어떤 값 체계인지(예: Confluence accountId / group name / groupId)는 **auth-server 의 Confluence OAuth 매핑이 결정**한다 → §4 확인. 색인은 그 체계를 그대로 따른다.
- 합성 토큰 `space:{spaceKey}` 는 JWT `groups` 에 동일 토큰이 실제로 실릴 때만 유효하다. JWT 가 Confluence group 식별자만 담는다면, 색인은 `space:{key}` 합성 대신 해당 스페이스 read 권한을 가진 **실제 group 식별자**로 적재한다(§2.5).

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
| 명시적 restriction 없음 (= 스페이스 상속/스페이스 멤버 공개) | 해당 스페이스 read 권한 group 을 **JWT vocabulary 와 동일한 식별자**로 `allowed_groups` 에 적재 (공개=스페이스 멤버 취급). JWT 가 `space:{key}` 토큰을 싣는 경우에 한해 그 토큰을 그대로 사용 |
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

1. **JWT `userId`/`groups` 의 vocabulary 확정** (pass-through 의 전제) — auth-server 의 Confluence OAuth 가 산출하는 값이 accountId / group name / groupId 중 무엇인지. 색인 측 payload 가 이 값에 그대로 맞춰져야 하므로 가장 먼저 고정해야 한다. 더불어 content restrictions 응답이 같은 식별자(특히 groupId)를 제공하는지 ML 확인 — 불일치 시 색인 단계에서 매핑 필요.
2. inherited 권한(부모/스페이스 상속)을 effective 로 산출하는 책임 위치 — Ingestion 내부 vs Confluence API 조합.
3. 첨부파일(`raw_attachments`) 권한이 부모 페이지 권한을 따르는지.
4. `/ml/query` 가 실시간 Confluence 호출을 일절 하지 않는다는 전제(§2-1) 재확인 — 본 ADR 도 이 전제에 의존.
