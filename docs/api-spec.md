# LINA API Spec

> 기준: 중간 발표(4주차) 데모 범위 + 이후 확장 계획
> 전제: 중간 발표 시 인증 하드코딩, 스페이스 고정, 로그인 제외
> 기획서 버전: v2.1.7 (Authorization Server 분리, 사용자 단위 검색 반영)

---

## Common Response Wrapper

모든 API는 아래 Wrapper 구조를 따른다.

**성공**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "요청 성공 메시지",
  "data": { ... }
}
```

**에러**
```json
{
  "isSuccess": false,
  "code": 404,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "해당 대화를 찾을 수 없습니다"
}
```

> **예외**: SSE 스트리밍 응답(`/api/conversations/{id}/chat`)은 Wrapper 미적용, 이벤트 스트림으로 전달.

**시간 표기 정책 (2026-05-21 확정)**
- 저장은 UTC(`Instant`)로 통일하고, **응답 JSON 의 모든 timestamp 는 KST(`+09:00`) 로 절대 전환해 반환한다.**
- 직렬화 예: `Instant` → `ZonedDateTime kst = instant.atZone(ZoneId.of("Asia/Seoul"))` → `2026-05-06T19:00:00+09:00`
- 본 문서의 모든 응답 예시는 KST 표기로 작성한다.

**공통 Request Header**
| Name | Type | Description | Required |
|------|------|-------------|----------|
| Content-Type | String | application/json | ✅ |
| Authorization | String | Bearer {JWT 토큰} | ✅ |

---

# 1. 외부 API (Frontend → BFF)

## 1-1. 챗봇 질의 (핵심) — SSE 스트리밍

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/api/conversations/{conversationId}/chat` |
| 설명 | 사용자 질문을 ML 서버로 전달하고 SSE 스트리밍으로 응답 중계 |

**Request Body**
```json
{ "question": "지난번 S3 버킷 권한 오류 때 어떻게 해결했어?" }
```

**Response (SSE 이벤트 스트림)** — Wrapper 미적용
```
event: token
data: {"content": "S3 권한 오류는"}

event: token
data: {"content": " IAM 정책을 수정하여"}

event: sources
data: {
  "sources": [
    {
      "title": "S3 트러블슈팅 가이드",
      "pageId": "12345",
      "spaceId": "98310",
      "spaceName": "Cloud Control Center",
      "url": "https://confluence.example.com/pages/12345",
      "updatedAt": "2026-04-15T18:30:00+09:00",
      "relevanceScore": 0.92
    }
  ]
}

event: verification
data: {
  "confidenceScore": 0.85,
  "verificationResult": "SUPPORTED"
}

event: done
data: {"messageId": "msg-uuid-001"}

event: error
data: {"code": "ML_SERVER_ERROR", "message": "답변 생성 중 오류가 발생했습니다"}
```

**이벤트 타입**
- `token` — 답변 청크 (스트리밍)
- `sources` — RAG 참조 문서 목록
- `verification` — 답변 신뢰도 검증 결과 (`SUPPORTED` / `PARTIALLY_SUPPORTED` / `NOT_SUPPORTED`)
- `done` — 스트림 종료, `messageId` 반환
- `error` — ML 서버 오류 발생 시 에러 정보 전달, 스트림 종료

> **Gateway 설정**: SSE 특성상 타임아웃 60초로 별도 설정 필요.

---

## 1-2. 대화 관리

### 새 대화 생성
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/api/conversations` |

**Response**
```json
{
  "isSuccess": true,
  "code": 201,
  "message": "새 대화 생성 성공",
  "data": {
    "conversationId": "conv-uuid-001",
    "title": "새 대화",
    "createdAt": "2026-05-06T19:00:00+09:00"
  }
}
```

### 대화 목록 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/api/conversations` |
| 설명 | 사이드바에 표시할 대화 목록 |

**Query Parameter**
| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| page | int | N | 0 | 페이지 번호 |
| size | int | N | 20 | 페이지 크기 |

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "대화 목록 조회 성공",
  "data": {
    "conversations": [
      {
        "conversationId": "conv-uuid-001",
        "title": "S3 권한 오류 해결 방법",
        "lastMessageAt": "2026-05-06T19:05:00+09:00",
        "messageCount": 4
      }
    ],
    "totalCount": 15,
    "page": 0,
    "size": 20
  }
}
```

### 대화 메시지 이력 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/api/conversations/{conversationId}/messages` |
| 설명 | 멀티턴 복원용 전체 메시지 이력 |

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "메시지 이력 조회 성공",
  "data": {
    "conversationId": "conv-uuid-001",
    "messages": [
      {
        "messageId": "msg-uuid-001",
        "role": "user",
        "content": "지난번 S3 버킷 권한 오류 때 어떻게 해결했어?",
        "createdAt": "2026-05-06T19:00:00+09:00"
      },
      {
        "messageId": "msg-uuid-002",
        "role": "assistant",
        "content": "S3 권한 오류는 IAM 정책을 수정하여 해결했습니다...",
        "sources": [
          {
            "title": "S3 트러블슈팅 가이드",
            "pageId": "12345",
            "spaceId": "98310",
            "spaceName": "Cloud Control Center",
            "url": "https://confluence.example.com/pages/12345",
            "updatedAt": "2026-04-15T18:30:00+09:00",
            "relevanceScore": 0.92
          }
        ],
        "confidenceScore": 0.85,
        "verificationResult": "SUPPORTED",
        "createdAt": "2026-05-06T19:00:05+09:00"
      }
    ]
  }
}
```

### 대화 제목 수정
| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URL | `/api/conversations/{conversationId}` |

**Request Body**
```json
{ "title": "S3 권한 오류 트러블슈팅" }
```

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "대화 제목 수정 성공",
  "data": {
    "conversationId": "conv-uuid-001",
    "title": "S3 권한 오류 트러블슈팅",
    "updatedAt": "2026-05-06T19:10:00+09:00"
  }
}
```

### 대화 삭제
| 항목 | 내용 |
|------|------|
| Method | `DELETE` |
| URL | `/api/conversations/{conversationId}` |

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "대화 삭제 성공",
  "data": null
}
```

---

## 1-3. 피드백

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/api/messages/{messageId}/feedback` |
| 설명 | 답변에 대한 좋아요/싫어요 피드백 |

**Request Body**
```json
{
  "rating": "like",
  "comment": "정확한 답변이었어요"
}
```
- `rating`: `"like"` | `"dislike"`
- `comment`: 선택 사항

**Response**
```json
{
  "isSuccess": true,
  "code": 201,
  "message": "피드백 등록 성공",
  "data": {
    "feedbackId": "fb-uuid-001",
    "messageId": "msg-uuid-002",
    "rating": "like",
    "createdAt": "2026-05-06T19:06:00+09:00"
  }
}
```

---

## 1-4. 데이터 수집 (관리자용)

### 수집 트리거
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/api/admin/ingest` |
| 설명 | 고정 스페이스의 Confluence 문서 수집 수동 트리거 |

**Request Body**
```json
{ "spaceKey": "CPC" }
```

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "데이터 수집 작업 시작",
  "data": {
    "jobId": "job-uuid-001",
    "status": "STARTED",
    "startedAt": "2026-05-06T19:00:00+09:00"
  }
}
```

### 수집 상태 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/api/admin/ingest/status/{jobId}` |

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "수집 상태 조회 성공",
  "data": {
    "jobId": "job-uuid-001",
    "status": "IN_PROGRESS",
    "totalPages": 150,
    "processedPages": 87,
    "failedPages": 2,
    "startedAt": "2026-05-06T19:00:00+09:00"
  }
}
```
- `status`: `"STARTED"` | `"IN_PROGRESS"` | `"COMPLETED"` | `"FAILED"`

---

# 2. 내부 API (BFF → ML 서버)

> Wrapper 적용 여부는 AI 담당 팀원과 합의 필요.

## 2-1. RAG 질의
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/ml/query` |
| 설명 | BFF가 ML 서버에 RAG 질의 요청, SSE 스트리밍 응답 |

**Request Body**
```json
{
  "question": "지난번 S3 버킷 권한 오류 때 어떻게 해결했어?",
  "conversationId": "conv-uuid-001",
  "history": [
    { "role": "user", "content": "S3 관련 장애 이력 알려줘" },
    { "role": "assistant", "content": "최근 S3 관련 장애는 3건이 있었습니다..." }
  ],
  "userId": "user-001",
  "groups": ["Cloud-Control-Center"],
  "spaceKey": "CPC"
}
```
- `history`: BFF가 DB에서 이전 대화 이력을 꺼내서 전달 (멀티턴용)
- `userId` / `groups`: ACL Pre-filtering, JWT 에서 추출 (2단계는 데모 고정값)
- `spaceKey`: 검색 대상 Confluence 스페이스. 2단계는 고정값(`lina.demo.fixed-space-key`)

> **Confluence 토큰 미포함 (2026-05-22 변경):** 권한은 수집 시 Qdrant payload(`allowed_groups`/`allowed_users`)에 ACL 로 저장되고, 질의 시 JWT 의 `user_id`/`groups` 로 필터링한다 (기획서 §6.4/§6.6). 따라서 RAG 질의 경로(`/ml/query`)는 라이브 Confluence 호출이 없어 `accessToken`/`cloudId` 가 불필요하며, 토큰은 크롤하는 수집 단계(`/ml/ingest`, §2-2)에서만 전달한다.
>
> ※ ML 확인 대기: `/ml/query` 가 실시간 Confluence 호출을 일절 하지 않음을 ML 팀과 확인한 뒤 본 결정을 확정한다. 확인 결과에 따라 토큰 전달 경로가 다시 조정될 수 있다.

**Response**: SSE 스트리밍 (외부 API와 동일 형식, BFF가 그대로 중계)

## 2-2. 데이터 수집 트리거
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/ml/ingest` |
| 설명 | Confluence 문서 수집 → 청킹 → 임베딩 파이프라인 실행 |

**Request Body**
```json
{
  "spaceKey": "CPC",
  "mode": "full",
  "accessToken": "<Confluence OAuth access_token>",
  "cloudId": "11111111-2222-3333-4444-555555555555"
}
```
- `mode`: `"full"` (전체) | `"delta"` (변경분만)
- `accessToken` / `cloudId` (**PoC 모드, 3단계 도입**): Confluence OAuth 로 발급한 access token 과 `accessible-resources`로 조회한 cloudId. BFF 가 auth-server 로부터 수신해 Data Ingestion Pipeline 으로 그대로 전달한다 — 수집 단계에서 Confluence REST API 호출 (페이지/첨부파일 크롤) 시 사용. **추후 확장 단계에서 `connectionId` + `cloudId` 조합으로 교체 예정** — 그때는 Data Ingestion 서버가 connectionId 로 BFF/auth-server 에 토큰을 재요청.

> **보안 주의 (PoC 모드 한정):** 요청 body 에 access token 이 평문으로 노출되므로 다음 운영 규칙을 함께 강제한다.
> - ML/BFF 로그·tracing 본문에 token 미수집 (마스킹 또는 본문 제외)
> - RabbitMQ 메시지·이벤트 페이로드에 token 미포함
> - actuator `env`/`heapdump`/`threaddump` 등 민감 endpoint 비노출
> - Data Ingestion Pipeline Pod 에 NetworkPolicy 적용해 호출자 제한
> - 확장 단계(`connectionId`)로의 전환 시점을 사전에 정한다.

## 2-3. 수집 상태 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/ml/ingest/status/{jobId}` |

Response: 외부 API `/api/admin/ingest/status/{jobId}`의 `data` 내부와 동일.

## 2-4. 헬스체크

ML 서버는 책임이 다른 두 파이프라인으로 분리되어 있으며, 각각의 헬스 엔드포인트도 분리해 관리한다.

### 2-4-1. RAG Pipeline 헬스체크
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/ml/rag/health` |
| 목적 | BFF 가 RAG Pipeline 서버(질의/응답 생성·AI Agent 워크플로)가 정상 응답 가능한지만 확인 |

**Response**
```json
{ "status": "UP" }
```

### 2-4-2. Data Ingestion Pipeline 헬스체크
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/ml/ingest/health` |
| 목적 | BFF 가 Data Ingestion Pipeline 서버(Confluence 수집/청킹/임베딩)가 정상 응답 가능한지만 확인 |

**Response**
```json
{ "status": "UP" }
```

### 공통 규칙

| status | 의미 |
|--------|------|
| `UP` | 대상 서버 정상 응답 |
| `DOWN` | 응답 불가 또는 오류 |

- BFF 는 Vector DB, LLM, Confluence API, RabbitMQ 등 ML 내부 의존성을 **전역적으로 health check 하지 않는다**. 각 서버(RAG Pipeline / Data Ingestion Pipeline)가 요청을 받아 응답할 수 있는 상태(reachable & responsive)인지만 확인한다.
- 내부 컴포넌트의 상세 상태 점검 책임은 각 ML 서버 자체에 둔다. BFF 는 그 상세 상태를 응답으로 받지 않는다.
- 두 엔드포인트는 독립적으로 평가된다. 한쪽이 `DOWN` 이어도 다른 한쪽이 `UP` 이면 해당 기능만 영향을 받는다 (예: Ingestion 다운 시 신규 수집만 차단, 기존 검색·질의는 정상).

> **Spring Boot 측 적용 노트**: 각 ML 서버 호출 경로에는 Resilience4j 등의 Circuit Breaker 를 적용해, ML 서버 장애가 BFF 전체로 전파되지 않도록 격리한다. Circuit Breaker 는 RAG Pipeline / Data Ingestion Pipeline 호출에 각각 독립적으로 적용하며, BFF 자체 헬스체크와는 분리한다.

---

# 3. 전체 호출 흐름

```
[프론트엔드]
  │
  ├─ POST /api/conversations                      → BFF → DB에 대화 생성
  ├─ POST /api/conversations/{id}/chat            → BFF → POST /ml/query (SSE)
  │                                                 ├─ DB에서 이전 이력 조회 → ML 전달
  │                                                 └─ ML 응답을 DB 저장 + SSE 중계
  ├─ GET    /api/conversations                    → BFF → DB 대화 목록 조회
  ├─ GET    /api/conversations/{id}/messages      → BFF → DB 메시지 이력 조회
  ├─ PATCH  /api/conversations/{id}               → BFF → DB 대화 제목 수정
  ├─ DELETE /api/conversations/{id}               → BFF → DB 대화 삭제
  ├─ POST   /api/messages/{id}/feedback           → BFF → DB 피드백 저장
  └─ POST   /api/admin/ingest                     → BFF → POST /ml/ingest → ML 서버
```

---

# 4. 중간 발표 이후 추가 예정 (5~7주차)

## 4-1. 인증 (5주차)
BFF → Authorization Server 위임 구조 (기획서 v2.1.7 반영)

| API | 설명 |
|-----|------|
| `GET /api/auth/login` | Confluence OAuth 로그인 리다이렉트 |
| `GET /api/auth/callback` | OAuth 콜백 처리, JWT 발급 |
| `POST /api/auth/refresh` | Refresh Token 기반 JWT 자동 갱신 |
| `POST /api/auth/logout` | 로그아웃, JWT 무효화 |
| `GET /api/users/me` | 현재 로그인 사용자 정보 조회 |

**`GET /api/users/me` Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "사용자 정보 조회 성공",
  "data": {
    "userId": "user-001",
    "name": "이다연",
    "email": "dayeon@example.com",
    "role": "USER",
    "profileImageUrl": "https://...",
    "lastLoginAt": "2026-05-20T18:00:00+09:00"
  }
}
```
- `role`: `"USER"` | `"ADMIN"`

## 4-2. 관리자 대시보드 (6주차)

| API | 설명 |
|-----|------|
| `GET /api/admin/stats` | 일간 질의 수, 평균 응답 시간, 시간대별 접속 추이 |
| `GET /api/admin/users` | 일일/전체 사용자 수, 사용자별 활동 요약 |
| `GET /api/admin/data` | 스페이스/페이지 수, VectorDB 용량, 최종 동기화 일시 |
| `GET /api/admin/feedback` | 긍정/부정 비율, 부정 피드백 원문, 피드백 추이 |
| `GET /api/admin/sync` | 동기화 이력 (성공/실패/소요 시간) |

**`GET /api/admin/stats` Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "서비스 통계 조회 성공",
  "data": {
    "dailyQueryCount": 142,
    "avgResponseTime": 3.2,
    "totalConversations": 856,
    "hourlyAccessTrend": [
      { "hour": 9, "count": 23 },
      { "hour": 10, "count": 45 }
    ]
  }
}
```

**`GET /api/admin/users` Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "사용자 현황 조회 성공",
  "data": {
    "totalUsers": 48,
    "dailyActiveUsers": 12,
    "users": [
      {
        "userId": "user-001",
        "name": "이다연",
        "conversationCount": 35,
        "lastAccessAt": "2026-05-20T18:00:00+09:00"
      }
    ]
  }
}
```

**`GET /api/admin/data` Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "데이터 현황 조회 성공",
  "data": {
    "totalSpaces": 5,
    "totalPages": 1230,
    "totalAttachments": 187,
    "vectorDbSize": "2.3 GB",
    "totalChunks": 8940,
    "lastSyncAt": "2026-05-20T17:00:00+09:00"
  }
}
```

**`GET /api/admin/sync` Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "동기화 이력 조회 성공",
  "data": {
    "syncHistory": [
      {
        "syncId": "sync-uuid-001",
        "status": "COMPLETED",
        "updatedPages": 12,
        "deletedPages": 1,
        "duration": 45,
        "completedAt": "2026-05-20T17:00:00+09:00"
      }
    ]
  }
}
```

## 4-3. Confluence 페이지 미리보기 (5주차 이후 — 인증 의존)

> 출처 hover preview 및 Chat 메인 추천 문서 preview에 사용한다.
> **선행 조건:** 3단계(Authorization Server)에서 OAuth Access Token이 MySQL에 암호화 저장된 이후에만 동작한다. 인증이 없는 중간발표(2단계) 범위에는 포함하지 않는다.

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/api/confluence/pages/preview` |
| 설명 | 출처 문서의 Confluence 페이지 HTML 본문 + 메타데이터 조회 |

**Query Parameter**
| Key | Type | Required | Description |
|-----|------|----------|-------------|
| page_id | string | ✅ | Confluence page ID |

**처리 방식**
- Frontend는 출처 목록 문서 hover 시 `page_id`를 담아 BFF에 요청한다.
- BFF는 서버에 저장된 OAuth 토큰으로 Confluence REST API를 호출한다.
- BFF는 Confluence 응답의 `body.view.value` HTML 문자열을 `bodyViewValue`로 변환해 반환한다.
- BFF는 Confluence 응답의 `space.name`, `ancestors[].title`, `title`을 조합해 `breadcrumbs`를 파생한다.
- Frontend는 `bodyViewValue`를 DOMPurify로 sanitize한 뒤 `v-html`로 렌더링한다.
- 원본 열기 아이콘 클릭 시 `pageUrl`을 새 탭에서 연다.

> **TBD (3단계에서 결정):** 저장된 OAuth 토큰으로 Confluence를 호출하는 주체 — (a) BFF가 토큰을 사용해 직접 호출 vs (b) Authorization Server 프록시. `backend/CLAUDE.md` §6(BFF는 Confluence 토큰을 직접 교환하지 않는다) / `docs/architecture.md`와 함께 3단계 착수 시 확정하고 본 절을 갱신한다.

**Response**
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "Confluence 페이지 미리보기 조회 성공",
  "data": {
    "pageId": "12345",
    "title": "S3 트러블슈팅 가이드",
    "spaceName": "Cloud Control Center",
    "authorName": "Platform Team",
    "updatedAt": "2026-04-15T18:30:00+09:00",
    "breadcrumbs": ["Cloud Control Center", "AWS", "S3", "S3 트러블슈팅 가이드"],
    "pageUrl": "https://confluence.example.com/pages/12345",
    "bodyViewValue": "<h1>S3 트러블슈팅 가이드</h1><p>S3 권한 오류는...</p>"
  }
}
```

**Error Response**
```json
{
  "isSuccess": false,
  "code": 404,
  "message": "Confluence 페이지 미리보기를 찾을 수 없습니다",
  "data": null
}
```

> **보안 주의:** BFF는 OAuth token을 Frontend에 노출하지 않는다. Frontend는 HTML 렌더링 전 반드시 sanitize한다.