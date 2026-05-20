# DB Schema

> 기준: 2단계 BFF Server 핵심 API (중간발표, 인증 없음).
> 본 문서는 BFF Server 가 직접 다루는 컬렉션의 구조·인덱스·결정 사항을 정의한다.
> 규칙: `docs/conventions.md` §11 (snake_case 컬럼/필드, 인덱스 목적 문서화).

---

## 1. 저장소 역할 분리

| 저장소 | 컬렉션/테이블 | 접근 | 비고 |
|---|---|---|---|
| **MongoDB (BFF CRUD)** | `conversations`, `messages`(`sources` 내장), `feedbacks` | BFF 가 읽기/쓰기 | 본 문서 §3 의 정의 대상 |
| **MongoDB (BFF 읽기 전용)** | `raw_pages`, `raw_attachments`, `attachment_texts`, `chunked_units`, `import_jobs`, `sync_logs` | BFF 는 조회만, 쓰기는 Ingestion/Sync Worker | RAG 파이프라인 입력 데이터 |
| **MongoDB (RAG 파이프라인 전용)** | `inference_logs`, `audit_logs`, `qca_dataset` | RAG 파이프라인이 관리. BFF 접근 없음 | 본 문서 범위 밖 |
| **MySQL (3단계)** | `users`, `user_tokens`, `user_space_acl`, `admins` | Authorization Server 가 관리 | 2단계에서는 미사용. 3단계 도입 시 별도 정의 |

`backend/CLAUDE.md` §2.2 / §6 의 MongoDB 쓰기 금지 규칙은 위 두 번째 행(RAG 파이프라인 데이터)에 한정된다. 대화/피드백 컬렉션은 BFF 가 정상 CRUD 한다.

---

## 2. 적용 결정 (확정)

| 항목 | 결정 | 근거 |
|---|---|---|
| 저장소 | **MongoDB 7.x** — `conversations`, `messages`, `feedbacks` 세 컬렉션. `message_sources`는 별도 컬렉션이 아니라 `messages.sources` 내장 배열로 단순화 | `backend/bff-server/current-plans.md` 확정된 결정 #4 (2026-05-20) |
| 삭제 방식 | **soft delete** — `conversations.deletedAt`, `messages.deletedAt`. 모든 조회는 `deletedAt == null` 필터 | 연결된 피드백·QCA 데이터 보존 |
| 피드백 재등록 | **메시지당 1건** — `uniq_feedbacks_message` 유니크 인덱스. 동일 메시지 재요청 시 동일 문서 upsert (Feature 6 에서 신규 201 / 갱신 200) | `backend/bff-server/current-plans.md` 확정된 결정 #1 |
| ID 전략 | `conversationId`/`messageId`/`feedbackId` 는 애플리케이션 생성 UUID(`String`)를 `_id` 로 사용 | 분산 생성·노출 안전. ObjectId 대신 UUID 로 외부 노출도 안전한 형식 |
| 시간 필드 | UTC `Date`(BSON). JPA 시절과 동일하게 `java.time.Instant` 로 매핑 (API ISO-8601 `Z` 표기와 일치) | `docs/api-spec.md` 응답 포맷 |

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
  "deletedAt": null
}
```

인덱스:

| 인덱스 | 정의 | 목적 |
|---|---|---|
| `idx_conversations_user_active_recent` | `{ userId: 1, deletedAt: 1, lastMessageAt: -1 }` | `findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc` — 사용자별 활성 대화 최신순 페이징. 컬럼 순서 = 등치(`userId`) → 필터(`deletedAt`) → 정렬(`lastMessageAt`). |

---

### 3.2 `messages`

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `_id` (`messageId`) | String (UUID) | ✅ | |
| `conversationId` | String | ✅ | 부모 대화 식별자 (참조 키, FK 제약은 없음) |
| `role` | String | ✅ | `USER` / `ASSISTANT` |
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
  "role": "ASSISTANT",
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
| `idx_conversations_user_active_recent` | `conversations` | `{ userId:1, deletedAt:1, lastMessageAt:-1 }` | 사용자별 활성 대화 최신순 페이징 |
| `idx_messages_conversation_active_created` | `messages` | `{ conversationId:1, deletedAt:1, createdAt:1 }` | 대화별 활성 메시지 시간순(멀티턴 복원) |
| `uniq_feedbacks_message` | `feedbacks` | `{ messageId:1 }` UNIQUE | 메시지당 피드백 1건 강제 |

---

## 6. 변경 이력

| 날짜 | 변경 | 비고 |
|---|---|---|
| 2026-05-19 | 최초 작성 — MySQL/JPA 기반 4테이블 정의 | DB 신규 도입 |
| 2026-05-20 | 저장소 전환 — MongoDB 3컬렉션(`messages.sources` 내장)으로 재정의. MySQL 은 3단계의 `users`/`user_tokens`/`user_space_acl`/`admins` 도입 시 별도 정의 | `backend/bff-server/current-plans.md` 확정된 결정 #4 |
