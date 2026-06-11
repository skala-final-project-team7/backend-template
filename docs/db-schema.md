# DB Schema

> 기준: 2단계 BFF Server 핵심 API (중간발표, 인증 없음).
> 본 문서는 BFF Server 가 직접 다루는 컬렉션의 구조·인덱스·결정 사항을 정의한다.
> 규칙: `docs/conventions.md` §11 (snake_case 컬럼/필드, 인덱스 목적 문서화).

---

## 변경 이력

| 날짜 | 변경 | 비고 |
|---|---|---|
| 2026-05-19 | 최초 작성 — MySQL/JPA 기반 4테이블 정의 | DB 신규 도입 |
| 2026-05-20 | 저장소 전환 — MongoDB 3컬렉션(`messages.sources` 내장)으로 재정의. MySQL 은 3단계의 `users`/`user_tokens`/`user_space_acl`/`admins` 도입 시 별도 정의 | `backend/bff-server/current-plans.md` 확정된 결정 #4 |
| 2026-05-29 | `conversations.isPinned`(채팅방 고정) 추가. `idx_conversations_user_active_recent` 에 `isPinned:-1` 정렬 컬럼 반영(고정 우선 정렬). PATCH 토글은 `docs/api-spec.md` §1-2 | FE 채팅방 고정 기능 |
| 2026-06-01 | `messages.role` 저장값을 `USER`/`ASSISTANT` → **`user`/`assistant`** (lowercase) 로 통일 — LLM/OpenAI 산업 표준, RAG `/ml/query` 와이어와 동일 값(boundary 변환 제거). Common Enum 표기 정책의 명시된 예외 | RAG boundary 매핑 제거·산업 표준 정렬 |
| 2026-06-02 | MySQL `users` 테이블 정의(§6.1) — `role` 컬럼(`USER`/`ADMIN`) 포함, JWT `role` claim 의 single source. 별도 `admins` 테이블 계획 흡수. 최초 admin 은 마이그레이션 스크립트에 하드코딩 INSERT | 권한 모델 DB 단일화·YAML config 미사용 |
| 2026-06-02 | `messages.content` 본문 검색 인덱스 권고(§3.2 후속) — `GET /api/conversations/search` (api-spec §1-2 신설) 를 위한 인덱스. PoC 는 `$regex`, 후속에 text index 전환 검토 | 대화 본문 검색 endpoint 도입 |
| 2026-06-10 | MySQL 3단계 스키마를 auth-server 실제 마이그레이션(`V001`~`V003`)에 맞춰 **재정의**(§6). `users` PK=`user_key`(BINARY16 UUID), `user_id`=Confluence accountId(UNIQUE, JWT/RAG `userId`), `email`(UNIQUE) 분리, LINA `access_token` 컬럼. `groups`→**`user_groups`**(1:N, `group_id`=groupId, 로그인 시 `memberof` 적재). Confluence OAuth access/refresh + `cloud_id`→**`user_tokens`**(앱 내 미리보기 라이브 호출, AES-GCM). LINA refresh token 은 후속 | auth-server Feature 1 SQL 확정 |
| 2026-06-11 | **`admin_atlassian_credential`** 테이블 신설(§6.4, `V004`). admin-key 관리(activate/deactivate) 전용 — `site_url` + `admin_api_token_enc`(AES-GCM, Basic auth). ingestion 콘텐츠 조회는 OAuth Bearer+게이트웨이(`user_tokens`)라 자격증명·URL 체계가 달라 분리. Feature 0 게이트(OAuth2 앱은 admin-key 접근 불가) + 하이브리드 모델(admin-key=API Token/site URL, 콘텐츠 조회=OAuth Bearer/gateway) 확정 반영 | auth-server Feature 0 게이트·#6 |
| 2026-06-11 | `users` 에 **`refresh_token`**(VARCHAR(512), NULL) 컬럼 선반영(§6.1) — **`V001` 직접 수정**. LINA 세션 refresh token 저장용, 발급/회전 로직은 Feature 4. ⚠️ 이미 적용한 로컬 DB 는 체크섬 불일치 → 재생성(drop&재마이그레이션) 또는 `flyway repair` 필요 | LINA refresh 컬럼 선반영 요청 |

---

## 1. 저장소 역할 분리

| 저장소 | 컬렉션/테이블 | 접근 | 비고 |
|---|---|---|---|
| **MongoDB (BFF CRUD)** | `conversations`, `messages`(`sources` 내장), `feedbacks` | BFF 가 읽기/쓰기 | 본 문서 §3 의 정의 대상 |
| **MongoDB (BFF 읽기 전용)** | `raw_pages`, `raw_attachments`, `attachment_texts`, `chunked_units`, `import_jobs`, `sync_logs` | BFF 는 조회만, 쓰기는 Ingestion/Sync Worker | RAG 파이프라인 입력 데이터 |
| **MongoDB (RAG 파이프라인 전용)** | `inference_logs`, `audit_logs`, `qca_dataset` | RAG 파이프라인이 관리. BFF 접근 없음 | 본 문서 범위 밖 |
| **MySQL (3단계)** | `users`(role 포함, §6.1), `user_groups`(§6.3), `user_tokens`(§6.2), `admin_atlassian_credential`(admin-key 관리, §6.4) | Authorization Server 가 관리 | 2단계에서는 미사용. 별도 `admins` 테이블 계획은 `users.role` 로 흡수(2026-06-02). `groups` 는 로그인 시 Confluence `memberof` 로 조회해 **`user_groups`** 에 적재→JWT claim(`groupId`). **`user_space_acl`(스페이스 단위 권한 테이블) 미사용** — 페이지 ACL 은 Qdrant payload(ADR 0001 §2). |

`backend/CLAUDE.md` §2.2 / §6 의 MongoDB 쓰기 금지 규칙은 위 두 번째 행(RAG 파이프라인 데이터)에 한정된다. 대화/피드백 컬렉션은 BFF 가 정상 CRUD 한다.

---

## 2. 적용 결정 (확정)

| 항목 | 결정 | 근거 |
|---|---|---|
| 저장소 | **MongoDB 7.x** — `conversations`, `messages`, `feedbacks` 세 컬렉션. `message_sources`는 별도 컬렉션이 아니라 `messages.sources` 내장 배열로 단순화 | `backend/bff-server/current-plans.md` 확정된 결정 #4 (2026-05-20) |
| 삭제 방식 | **soft delete** — `conversations.deletedAt`, `messages.deletedAt`. 모든 조회는 `deletedAt == null` 필터 | 연결된 피드백·QCA 데이터 보존 |
| 피드백 재등록 | **메시지당 1건** — `uniq_feedbacks_message` 유니크 인덱스. 동일 메시지 재요청 시 동일 문서 upsert (Feature 6 에서 신규 201 / 갱신 200) | `backend/bff-server/current-plans.md` 확정된 결정 #1 |
| ID 전략 | `conversationId`/`messageId`/`feedbackId` 는 애플리케이션 생성 UUID(`String`)를 `_id` 로 사용 | 분산 생성·노출 안전. ObjectId 대신 UUID 로 외부 노출도 안전한 형식 |
| 시간 필드 | 저장은 UTC `Date`(BSON), `java.time.Instant` 로 매핑. **응답 JSON 은 KST(`+09:00`) 로 절대 전환해 반환** (2026-05-21 확정) | `docs/api-spec.md` Common 시간 표기 정책 |

> **인덱스 생성:** Spring Data MongoDB 의 `spring.data.mongodb.auto-index-creation: true` 로 엔티티의 `@Indexed`/`@CompoundIndex` 가 부팅 시 자동 생성된다. 운영 환경에서는 동일 정의의 인덱스 생성 스크립트를 미리 적용해도 무방하다(중복 정의는 idempotent).

---

## 3. 컬렉션 정의

### 3.1 `conversations`

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `_id` (`conversationId`) | String (UUID) | ✅ | 애플리케이션 생성 UUID |
| `userId` | String | ✅ | 2단계는 고정 데모 사용자 |
| `title` | String | ✅ | |
| `createdAt` | Date (UTC) | ✅ | |
| `updatedAt` | Date (UTC) | ✅ | |
| `lastMessageAt` | Date (UTC) | ✅ | 목록 정렬 키 |
| `isPinned` | Boolean | ✅ | 기본 `false`. 채팅방 고정 여부(목록 정렬 시 상단 우선) |
| `deletedAt` | Date (UTC) | — | nullable. soft delete |

샘플 문서:

```json
{
  "_id": "conv-uuid-001",
  "userId": "user-001",
  "title": "S3 권한 오류 트러블슈팅",
  "createdAt": { "$date": "2026-05-06T10:00:00Z" },
  "updatedAt": { "$date": "2026-05-06T10:10:00Z" },
  "lastMessageAt": { "$date": "2026-05-06T10:05:00Z" },
  "isPinned": false,
  "deletedAt": null
}
```

인덱스:

| 인덱스 | 정의 | 목적 |
|---|---|---|
| `idx_conversations_user_active_recent` | `{ userId: 1, deletedAt: 1, isPinned: -1, lastMessageAt: -1 }` | `findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc` — 사용자별 활성 대화를 고정 우선·최신순 페이징. 컬럼 순서 = 등치(`userId`) → 필터(`deletedAt`) → 정렬(`isPinned` 우선 → `lastMessageAt`). |

---

### 3.2 `messages`

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `_id` (`messageId`) | String (UUID) | ✅ | |
| `conversationId` | String | ✅ | 부모 대화 식별자 (참조 키, FK 제약은 없음) |
| `role` | String | ✅ | `user` / `assistant` (lowercase — LLM/OpenAI 산업 표준, RAG `/ml/query` `history[].role` 과 동일 값) |
| `content` | String | ✅ | 본문(길이 제한 없음. MongoDB 문서당 16MB 한도 내) |
| `sources` | Array<Embedded> | — | assistant 메시지의 인용 출처. user 메시지는 빈 배열 |
| `sources[].title` | String | — | 페이지 제목 |
| `sources[].pageId` | String | — | Confluence page ID |
| `sources[].spaceId` | String | — | |
| `sources[].spaceName` | String | — | |
| `sources[].url` | String | — | 원본 링크 |
| `sources[].sourceUpdatedAt` | Date (UTC) | — | Confluence 페이지 최종 수정일 |
| `sources[].relevanceScore` | Double | — | RAG 관련도 점수 |
| `confidenceScore` | Double | — | RAG 답변 신뢰도. assistant 메시지에만 |
| `verificationResult` | String | — | `SUPPORTED` / `PARTIALLY_SUPPORTED` / `NOT_SUPPORTED` |
| `createdAt` | Date (UTC) | ✅ | |
| `deletedAt` | Date (UTC) | — | nullable. soft delete |

샘플 문서:

```json
{
  "_id": "msg-uuid-002",
  "conversationId": "conv-uuid-001",
  "role": "assistant",
  "content": "S3 권한 오류는 IAM 정책을 수정하여 해결했습니다...",
  "sources": [
    {
      "title": "S3 트러블슈팅 가이드",
      "pageId": "12345",
      "spaceId": "98310",
      "spaceName": "Cloud Control Center",
      "url": "https://confluence.example.com/pages/12345",
      "sourceUpdatedAt": { "$date": "2026-04-15T09:30:00Z" },
      "relevanceScore": 0.92
    }
  ],
  "confidenceScore": 0.85,
  "verificationResult": "SUPPORTED",
  "createdAt": { "$date": "2026-05-06T10:00:05Z" },
  "deletedAt": null
}
```

인덱스:

| 인덱스 | 정의 | 목적 |
|---|---|---|
| `idx_messages_conversation_active_created` | `{ conversationId: 1, deletedAt: 1, createdAt: 1 }` | `findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc` — 대화별 활성 메시지 시간순(멀티턴 복원). |
| (후속) `idx_messages_content_text` | `{ content: "text" }` | `GET /api/conversations/search` 본문 검색 가속용 — PoC 는 `$regex` (case-insensitive), 후속 라운드에서 text index 도입 시 `$text` 로 전환 검토 (단, `$regex` 와 `$text` 는 매칭 시맨틱이 달라 cutover 시 결과 차이 검증 필요). |

> **내장 배열 결정:** 출처는 메시지와 함께만 의미를 가지며 메시지 단위로 같이 읽힌다. 별도 컬렉션 + 조인 대신 `sources` 내장이 1회 read 로 끝나 효율적이다. (조회 패턴: `docs/api-spec.md` §1-2 메시지 이력 응답에서 메시지와 함께 sources 를 반환)

---

### 3.3 `feedbacks`

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `_id` (`feedbackId`) | String (UUID) | ✅ | |
| `messageId` | String | ✅ | 대상 assistant 메시지 식별자. UNIQUE |
| `rating` | String | ✅ | `LIKE` / `DISLIKE` |
| `comment` | String | — | nullable |
| `createdAt` | Date (UTC) | ✅ | |

샘플 문서:

```json
{
  "_id": "fb-uuid-001",
  "messageId": "msg-uuid-002",
  "rating": "LIKE",
  "comment": "정확한 답변이었어요",
  "createdAt": { "$date": "2026-05-06T10:06:00Z" }
}
```

인덱스:

| 인덱스 | 정의 | 목적 |
|---|---|---|
| `uniq_feedbacks_message` | `{ messageId: 1 }` UNIQUE | 메시지당 피드백 1건 보장. 재등록은 동일 문서 upsert (Feature 6). QCA 연결은 `messageId`(assistant) → `conversationId` 기준 직전 `USER` 메시지로 추적 (`backend/rules/domains.md` §2). |

---

## 4. 참조 관계 요약

문서 간 참조는 FK 제약 없이 `*Id` 필드로 표현한다(MongoDB 는 FK 제약을 강제하지 않음). 무결성은 애플리케이션 계층에서 보장한다.

| 자식 | 필드 | 부모 |
|---|---|---|
| `messages` | `conversationId` | `conversations._id` |
| `messages[].sources` | (내장) | `messages` 내부 |
| `feedbacks` | `messageId` | `messages._id` |

---

## 5. 인덱스 요약

| 인덱스 | 컬렉션 | 정의 | 목적 |
|---|---|---|---|
| `idx_conversations_user_active_recent` | `conversations` | `{ userId:1, deletedAt:1, isPinned:-1, lastMessageAt:-1 }` | 사용자별 활성 대화 고정 우선·최신순 페이징 |
| `idx_messages_conversation_active_created` | `messages` | `{ conversationId:1, deletedAt:1, createdAt:1 }` | 대화별 활성 메시지 시간순(멀티턴 복원) |
| `uniq_feedbacks_message` | `feedbacks` | `{ messageId:1 }` UNIQUE | 메시지당 피드백 1건 강제 |

---

## 6. MySQL 테이블 (3단계)

> 3단계 Authorization Server 도입 시 사용. 본 절은 plan-ahead 정의이며 실제 마이그레이션은 3단계 착수 시 적용한다.

### 6.1 `users`

| 컬럼 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `user_key` | BINARY(16) **PK** | ✅ | 내부 PK. 앱이 UUID 생성 후 `UUID_TO_BIN` 으로 저장 |
| `user_id` | VARCHAR(128) **UNIQUE** | ✅ | Confluence accountId (예: `712020:91b5112c-...`). 문서/JWT/RAG `/ml/query`/Qdrant `allowed_users` 의 `userId` 와 동일 식별자. **이메일 아님** |
| `email` | VARCHAR(255) **UNIQUE** | ✅ | 로그인 이메일 |
| `name` | VARCHAR(128) | — | 표시 이름 (Confluence 응답에서 저장) |
| `profile_image_url` | VARCHAR(512) | — | |
| `role` | ENUM(`USER`, `ADMIN`) | ✅ | 권한 역할. JWT `role` claim 의 **source of truth** (DB 단일). 별도 `admins` 테이블 흡수 |
| `access_token` | VARCHAR(512) | ✅ | **LINA 발급** access token(세션). Confluence 토큰 아님(§6.2) |
| `refresh_token` | VARCHAR(512) | — | **LINA 발급** refresh token(세션 갱신). 컬럼 선반영(`V001`, NULL 허용) — 발급/회전(rotating)은 Feature 4 |
| `last_login_at` | DATETIME (UTC) | — | OAuth callback 시 갱신 |
| `created_at` | DATETIME (UTC) | ✅ | INSERT 시각 |
| `updated_at` | DATETIME (UTC) | ✅ | 갱신 시각 (`ON UPDATE CURRENT_TIMESTAMP`) |

> **`user_id`(=accountId) vs `email`:** `user_id` 는 Confluence accountId 로 문서·JWT·RAG 전반의 `userId` 다(이메일 아님). `email` 은 로그인 식별자. PK 는 내부 UUID(`user_key`)이며, 외부 식별자(`user_id`/`email`)에 각각 UNIQUE 를 둔다.

**역할 결정 (auth-server)**

- OAuth callback 에서 Confluence accountId 받음 → `SELECT role FROM users WHERE user_id = ?`
- 행 없음 → `INSERT (..., role = 'USER')` 기본 / 행 있음 → 그 `role` 사용
- JWT `role` claim 으로 발급 — **config 분기 없이 DB 단일 source**(YAML bootstrap 미사용)

**최초 admin seed (PoC)**

첫 배포 마이그레이션에 admin 을 **하드코딩 INSERT**(`role='ADMIN'`). `user_key` 는 `UUID_TO_BIN(UUID())`, NOT NULL 컬럼(`user_id`/`email`/`access_token`)을 함께 지정한다. `access_token` 은 첫 로그인 전이라 placeholder 로 넣고 로그인 시 갱신한다(또는 컬럼 nullable 검토).

```sql
-- 예시
INSERT INTO users (user_key, user_id, email, name, role, access_token)
VALUES (UUID_TO_BIN(UUID()), '712020:91b5112c-...', 'admin@example.com', 'yhlee', 'ADMIN', 'SEED_PLACEHOLDER');
```

**별도 `admins` 테이블 미사용** — `users.role` 컬럼으로 흡수(2026-06-02 결정).

인덱스:

| 인덱스 | 정의 | 목적 |
|---|---|---|
| PK | `user_key` | 내부 조인 키(`user_groups`/`user_tokens` FK 대상) |
| `uk_users_user_id` UNIQUE | `user_id` | OAuth callback 시 accountId lookup·중복 계정 방지 |
| `uk_users_email` UNIQUE | `email` | 이메일 로그인 식별·중복 방지 |

### 6.2 `user_tokens`

Confluence OAuth access/refresh token + `cloud_id` 저장. **앱 내 Confluence 페이지 미리보기**(라이브 REST 호출) 및 **ingestion 콘텐츠 조회**(OAuth Bearer + 게이트웨이 URL `/ex/confluence/{cloudId}/...`)에 사용한다 — 챗(`/ml/query`)은 토큰 불필요. 사용자당 1:1. (admin-key 관리용 정적 API Token·siteUrl 은 본 테이블이 아니라 §6.4 `admin_atlassian_credential` 에 별도 보관.)

| 컬럼 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `user_key` | BINARY(16) **PK**, FK→`users` | ✅ | 1:1. `ON DELETE CASCADE` |
| `confluence_access_token_enc` | VARBINARY(2048) | ✅ | Confluence OAuth access token. **AES-GCM 암호화**(평문 금지). 미리보기·ingestion 콘텐츠 조회 Bearer |
| `confluence_refresh_token_enc` | VARBINARY(2048) | ✅ | Confluence OAuth refresh token. **AES-GCM 암호화**. rotating |
| `cloud_id` | VARCHAR(64) | ✅ | 게이트웨이 콘텐츠 조회 URL(`api.atlassian.com/ex/confluence/{cloudId}/...`) 구성용. 평문(민감 아님) |
| `access_token_expires_at` | DATETIME (UTC) | ✅ | access 만료 시각. 임박 시 refresh 로 갱신 |
| `created_at` / `updated_at` | DATETIME (UTC) | ✅ | |

> Atlassian access token 이 길어 암호화 컬럼은 `VARBINARY(2048)`. OAuth access/refresh 토큰은 callback 시 적재하고, 만료 임박이면 refresh 로 갱신(rotating — 저장소 덮어쓰기, 이전 값 미보존).

### 6.3 `user_groups`

사용자 Confluence group 멤버십(1:N). 로그인(OAuth callback) 시 `memberof` API 로 조회해 적재한다. JWT `groups`·Qdrant `allowed_groups` 와 동일 vocabulary(`groupId`).

| 컬럼 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `user_key` | BINARY(16), FK→`users` | ✅ | `ON DELETE CASCADE` |
| `group_id` | VARCHAR(128) | ✅ | Confluence groupId (`memberof` 응답 `results[].id`). group `name` 아님 |

인덱스:

| 인덱스 | 정의 | 목적 |
|---|---|---|
| PK(복합) | `(user_key, group_id)` | 동일 group 중복 방지 + `user_key` 기준 멤버십 조회 |

> groups 를 매 요청 `memberof` 재조회하는 대신 본 테이블에 영속(로그인 시 적재). 스페이스 단위 `user_space_acl` 은 미사용 — 페이지-단위 ACL 은 수집 단계 Qdrant payload (`docs/adr/0001-page-level-acl-source.md` §2).

> **`user_space_acl` 미사용 (2026-06-09 결정).** 사용자 `groups` 는 인증 시 Confluence group 멤버십 API(`memberof`)로 조회해 JWT `groups` claim(**`groupId`**)에 적재하고, 페이지-단위 ACL 은 수집 단계 Qdrant payload(`allowed_groups`/`allowed_users`)에 저장한다 → RDB per-user space ACL 테이블 불필요 (`docs/adr/0001-page-level-acl-source.md` §2).

### 6.4 `admin_atlassian_credential`

admin(role=ADMIN) 의 Atlassian 계정 credential. **Admin Key 수명주기 관리(activate/deactivate) 전용**(`V004`, 2026-06-11). ingestion 의 **콘텐츠 조회**(OAuth Bearer + 게이트웨이)와 자격증명·base URL 이 다르므로 `user_tokens` 와 분리한다.

| 컬럼 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `user_key` | BINARY(16) **PK**, FK→`users` | ✅ | admin 사용자. 1:1. `ON DELETE CASCADE` |
| `site_url` | VARCHAR(255) | ✅ | Admin Key 관리 API base URL. `POST/DELETE {site_url}/wiki/api/v2/admin-key` (콘텐츠 조회 게이트웨이와 별개). accessible-resources(AUTH-04) 응답 `url` 에서 확보 |
| `admin_api_token_enc` | VARBINARY(2048) | ✅ | Atlassian 계정 발급 API Token. **AES-GCM 암호화**(평문 금지). Basic auth=`base64(users.email:토큰)`. 정적 — 만료/refresh 없음 |

> **왜 별도 테이블인가**: admin-key 관리 API 는 OAuth2 앱 접근 불가(공식)라 **API Token Basic auth**로 호출해야 하고 base URL 도 **site URL**(`{site}.atlassian.net`)이다. 반면 ingestion 콘텐츠 조회는 admin 의 **OAuth Bearer** + 게이트웨이(`/ex/confluence/{cloudId}/...`)를 쓴다(→ `user_tokens`). 자격증명·URL 체계가 달라 분리. adminEmail 은 `users.email` 재사용. (Feature 0 게이트·api-spec §1-4/§2-5)
