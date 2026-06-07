# bff-server Current Plans

> `bff-server` 모듈의 현재 진행 중인 Plan. 세션 간 인수인계용 (`docs/ai/workflow.md` §6.3).
> 모듈 전용 규칙은 `backend/bff-server/CLAUDE.md` 참조.
> 3단계(Authorization Server) Plan 은 `backend/auth-server/current-plans.md` 로 분리.
> 2026-05-15: 중간발표 대비로 2단계(BFF Server) ↔ 3단계(Authorization Server) 순서 변경.
> 2026-05-20: 디렉토리 재정리 — `docs/ai/current-plan.md` → 본 파일로 이관.

---

## 진행 상태 요약

| 단계 | 범위 | 상태 |
|---|---|---|
| 1단계 | 프로젝트 초기 셋업 (패키지 구조, 설정, 공통 예외/응답) | ✅ 완료 (2026-05-15, `auth-server` 와 공동) |
| 2단계 | BFF Server 핵심 API (대화 CRUD, RAG 호출 + SSE 중계, 메시지 이력, 피드백, DB 스키마) | 🚧 Feature 1 완료, Feature 2~ 미착수 |
| 4단계 | 부가 API (관리자 대시보드 · Confluence 미리보기) | 미착수 |

> **2026-05-19 범위 조정:** 중간발표를 인증 없이 시연하기 위해 2단계에서 **JWT 검증 필터를 제외**하고 3단계로 이동한다. 대신 **피드백 API**를 4단계에서 2단계로 당긴다. 근거: 사용자 지시 + `docs/api-spec.md` 전제("중간 발표 시 인증 하드코딩, 로그인 제외"). 상세는 아래 **2단계** 섹션 참조.

---

## 확정된 기술 결정

| 항목 | 결정 |
|---|---|
| 빌드 도구 | Gradle (Groovy DSL) — `build.gradle`, `settings.gradle` |
| Java / Spring Boot | Java 21 / Spring Boot 3.3.x |
| 모듈 구성 | 단일 Gradle 멀티모듈 (`backend/`) |
| Base package | `com.lina` (`com.lina.common`, `com.lina.bff`, `com.lina.auth`) |
| Lombok | 사용 |
| Lint / Format | Spotless (google-java-format) + Checkstyle |

---

# 1단계 — 프로젝트 초기 셋업

## 작업 요약

`backend/` 디렉토리에 Spring Boot 3.x + Java 21 기반 Gradle 멀티모듈 프로젝트의 골격을 구성한다.
실제 비즈니스 로직(OAuth, 대화, 피드백 등)은 본 단계에서 구현하지 않는다.

구성 모듈:
- `common` — 공통 응답/예외/Error Code (다른 모듈이 의존)
- `bff-server` — BFF Server 골격 (Spring MVC + Virtual Threads)
- `auth-server` — Authorization Server 골격

---

## Feature 1. Gradle 멀티모듈 Root 셋업

### 변경 대상 파일
- `backend/settings.gradle`
- `backend/build.gradle`
- `backend/gradle.properties`
- `backend/gradle/wrapper/gradle-wrapper.properties`
- `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/gradle-wrapper.jar`
- `backend/.gitignore`
- `backend/config/checkstyle/checkstyle.xml`

### 체크리스트
- [x] `settings.gradle`에 `common`, `bff-server`, `auth-server` 모듈 include
- [x] root `build.gradle`에서 Java 21 toolchain, repositories, 공통 의존성 정의
- [x] root `build.gradle`에서 `org.springframework.boot` 플러그인은 `apply false`로 선언 (서버 모듈에서만 적용)
- [x] Spring Cloud BOM (`2023.0.x`) 적용
- [x] Spotless 플러그인 + google-java-format 설정
- [x] Checkstyle 플러그인 + Google Style 기반 룰 적용
- [x] Gradle Wrapper(8.x) 추가
- [x] `.gitignore` 갱신: `build/`, `.gradle/`, `out/`, `*.log`, `.idea/`, `*.iml`, `HELP.md`
- [x] `cd backend && ./gradlew projects` 정상 동작 확인

### 위험 요소
- Gradle wrapper 추가는 외부 다운로드가 필요할 수 있음 — 실패 시 사용자에게 보고

---

## Feature 2. `common` 모듈 — 공통 응답 포맷

### 변경 대상 파일
- `backend/common/build.gradle`
- `backend/common/src/main/java/com/lina/common/response/ApiResponse.java`
- `backend/common/src/main/java/com/lina/common/response/ErrorResponse.java`
- `backend/common/src/test/java/com/lina/common/response/ApiResponseTest.java`

### 체크리스트
- [x] `common/build.gradle`: 라이브러리 모듈 (`bootJar { enabled = false }`, `jar { enabled = true }`)
- [x] `ApiResponse<T>`: `success=true`, `data`, `message=null` 필드
- [x] `ApiResponse` 정적 팩토리 `success(T)`, `success(T, String)` 제공
- [x] `ErrorResponse`: `success=false`, `error: { code, message }` 중첩 구조
- [x] JSON 직렬화 결과가 `docs/conventions.md` §10 예시와 일치
- [x] `ApiResponseTest` — 성공 응답 직렬화 검증

---

## Feature 3. `common` 모듈 — 공통 예외 처리

### 변경 대상 파일
- `backend/common/src/main/java/com/lina/common/exception/ErrorCode.java`
- `backend/common/src/main/java/com/lina/common/exception/BizException.java`
- `backend/common/src/main/java/com/lina/common/exception/GlobalExceptionHandler.java`
- `backend/common/src/test/java/com/lina/common/exception/GlobalExceptionHandlerTest.java`

### 체크리스트
- [x] `ErrorCode` enum 정의 (`httpStatus`, `code`, `defaultMessage` 필드)
- [x] 초기 항목 추가 (`backend/CLAUDE.md` §4 매트릭스):
  - [x] `INVALID_REQUEST` (400)
  - [x] `UNAUTHORIZED` (401)
  - [x] `FORBIDDEN` (403)
  - [x] `RESOURCE_NOT_FOUND` (404)
  - [x] `EXTERNAL_SERVICE_ERROR` (502)
  - [x] `INTERNAL_ERROR` (500)
- [x] `BizException` — `RuntimeException` 상속, `ErrorCode` 및 override message 보유
- [x] `GlobalExceptionHandler` (`@RestControllerAdvice`) 처리:
  - [x] `BizException`
  - [x] `MethodArgumentNotValidException` (400)
  - [x] `ConstraintViolationException` (400)
  - [x] `AccessDeniedException` (403)
  - [x] `AuthenticationException` (401)
  - [x] fallback `Exception` (500)
- [x] 스택트레이스 / 내부 구현 정보 응답 비노출 확인 (`docs/conventions.md` §6.3)
- [x] `GlobalExceptionHandlerTest` — 각 케이스에 대한 HTTP status / body 검증

---

## Feature 4. `bff-server` 모듈 골격

### 변경 대상 파일
- `backend/bff-server/build.gradle`
- `backend/bff-server/src/main/java/com/lina/bff/BffApplication.java`
- `backend/bff-server/src/main/java/com/lina/bff/{chat,feedback,user,admin,rag/client,config,security,support}/.gitkeep`
- `backend/bff-server/src/main/resources/application.yml`
- `backend/bff-server/src/main/resources/application-local.yml`
- `backend/bff-server/src/main/resources/application-dev.yml`
- `backend/bff-server/src/main/resources/application-prod.yml`
- `backend/bff-server/src/test/java/com/lina/bff/BffApplicationTests.java`

### 체크리스트
- [x] `build.gradle`: `org.springframework.boot` 플러그인 적용
- [x] 의존성: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-cloud-starter-gateway-mvc`, `spring-boot-starter-actuator`, `lombok`, `project(':common')`
- [x] `BffApplication` — `@SpringBootApplication(scanBasePackages = {"com.lina.bff", "com.lina.common"})`
- [x] 패키지 골격 (`.gitkeep`): `chat/`, `feedback/`, `user/`, `admin/`, `rag/client/`, `config/`, `security/`, `support/`
- [x] `application.yml`: `server.port`, `spring.application.name`, `spring.threads.virtual.enabled: true`, actuator endpoint, logging 패턴
- [x] profile별 yml 분리 (local/dev/prod)
- [x] 외부 서비스 URL은 `${ML_PIPELINE_BASE_URL}` 형식 환경 변수 참조
- [x] **평문 secret 미포함** 확인
- [x] `BffApplicationTests.contextLoads()` 통과
- [x] `WebFlux`, `Mono`, `Flux` 의존성/타입 미사용 확인 (`backend/CLAUDE.md` §2.1)

---

## Feature 5. `auth-server` 모듈 골격

### 변경 대상 파일
- `backend/auth-server/build.gradle`
- `backend/auth-server/src/main/java/com/lina/auth/AuthApplication.java`
- `backend/auth-server/src/main/java/com/lina/auth/{oauth,jwt,token,config,support}/.gitkeep`
- `backend/auth-server/src/main/resources/application.yml`
- `backend/auth-server/src/main/resources/application-local.yml`
- `backend/auth-server/src/main/resources/application-dev.yml`
- `backend/auth-server/src/main/resources/application-prod.yml`
- `backend/auth-server/src/test/java/com/lina/auth/AuthApplicationTests.java`

### 체크리스트
- [x] `build.gradle`: `org.springframework.boot` 플러그인 적용
- [x] 의존성: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `lombok`, `project(':common')`
- [x] JWT 라이브러리 의존성 선언: `io.jsonwebtoken:jjwt-api/impl/jackson` (실제 발급은 2단계)
- [x] `AuthApplication` — `@SpringBootApplication(scanBasePackages = {"com.lina.auth", "com.lina.common"})`
- [x] 패키지 골격 (`.gitkeep`): `oauth/`, `jwt/`, `token/`, `config/`, `support/`
- [x] `application.yml`: `server.port` (BFF와 다르게), `spring.application.name`, actuator endpoint
- [x] profile별 yml 분리 (local/dev/prod)
- [x] OAuth client-id/secret은 환경 변수 참조만 사용 — **평문 secret 미포함**
- [x] `AuthApplicationTests.contextLoads()` 통과

---

## Feature 6. 검증

### 체크리스트
- [x] `cd backend && ./gradlew build` 성공
- [x] `./scripts/format.sh` 성공
- [x] `./scripts/lint.sh` 성공
- [x] `./scripts/test.sh` 성공
- [x] `./scripts/verify.sh` 성공
- [x] `./gradlew :bff-server:bootRun` 기동 가능 확인
- [x] `./gradlew :auth-server:bootRun` 기동 가능 확인
- [x] `git diff` 기준 의도하지 않은 변경 없음

---

## 1단계 수정하지 않는 파일

- `CLAUDE.md`, `backend/CLAUDE.md`, `backend/rules/*`
- `docs/architecture.md`, `docs/conventions.md`
- `scripts/*.sh`
- `docs/api-spec.md`, `docs/db-schema.md` (2/3단계에서 초안 작성)

## 1단계 문서 수정 필요 여부

- `docs/api-spec.md` — 불필요 (Public API 변경 없음)
- `docs/db-schema.md` — 불필요 (DB 변경 없음)
- `docs/architecture.md` — 불필요 (기존 정의 구조 준수)
- `docs/conventions.md` — 불필요

## 1단계 완료 기준 (Done Definition)

- [x] 위 Feature 1~6의 모든 체크박스가 체크됨
- [x] `ApiResponse`, `ErrorResponse`의 JSON 구조가 `docs/conventions.md` §10과 일치
- [x] `GlobalExceptionHandler`가 모든 정의된 `ErrorCode`에 대해 적절한 HTTP status 반환
- [x] 평문 secret이 어디에도 포함되지 않음
- [x] `BffApplication`, `AuthApplication` 모두 정상 기동

---

1단계 완료. 2단계 Plan은 아래 참조.

---

# 2단계 — BFF Server 핵심 API

> 작성일: 2026-05-19 · 담당 영역: Backend (BFF Server) · 상태: Plan 작성 완료, 코드 미착수
> 참조 규칙: `backend/CLAUDE.md`, `backend/rules/domains.md`, `backend/rules/rag-pipeline.md`, `backend/rules/testing.md`
> (`backend/rules/auth.md`는 인증을 3단계로 미루므로 §3 금지 사항만 적용 — 우회 코드 작성 금지 원칙 유지)

## 작업 요약

`bff-server` 모듈에 중간발표 데모용 핵심 API를 구현한다. **인증 없이** 동작하도록 하되, 인증 우회 코드를 production 분기로 심지 않고 별도 설정/컴포넌트로 분리한다.

구현 대상 (외부 API, `docs/api-spec.md` §1 기준):

| API | Method | URL |
|---|---|---|
| 새 대화 생성 | POST | `/api/conversations` |
| 대화 목록 조회 | GET | `/api/conversations` |
| 대화 메시지 이력 조회 | GET | `/api/conversations/{conversationId}/messages` |
| 대화 수정(제목/고정) | PATCH | `/api/conversations/{conversationId}` |
| 대화 삭제 | DELETE | `/api/conversations/{conversationId}` |
| 챗봇 질의 (SSE 중계) | POST | `/api/conversations/{conversationId}/chat` |
| 피드백 등록 | POST | `/api/messages/{messageId}/feedback` |

제외 (명시적): JWT 검증/OAuth(3단계), 관리자 대시보드 API(4단계), `/api/admin/ingest`(4단계), MongoDB RAG 파이프라인 데이터(`raw_pages`/`chunked_units` 등) 쓰기, RabbitMQ 발행.

## 인증 부재 처리 방침 (중요)

`backend/rules/auth.md` §3 "인증 흐름 우회 코드 작성 금지" 및 `CLAUDE.md` 절대 규칙을 위반하지 않기 위해 다음 방침을 따른다.

- production Controller/Service에 `if (인증 비활성화)` 류 조건 분기를 **추가하지 않는다.**
- 인증 사용자 정보(`userId`, `groups`)는 `CurrentUserProvider` 인터페이스로 추상화한다.
  - 2단계: `FixedDemoUserProvider` 구현체 — 설정값(`lina.demo.fixed-user-id`, `lina.demo.fixed-groups`)에서 고정 사용자 반환.
  - 3단계: JWT Claim 기반 구현체로 **교체만** 하면 되도록 인터페이스 경계를 둔다 (Controller/Service 재작성 불필요).
- `bff-server`는 이미 `spring-boot-starter-security`를 포함하므로, 데모용 `SecurityFilterChain`(전 경로 `permitAll`, CSRF 비활성)을 `config/`에 둔다. 이 빈에는 표준 주석 블록으로 "중간발표 한정, 3단계에서 JWT 검증으로 대체" 명시 + `NOTE:` 마커 작성.
- 위 방침은 `docs/api-spec.md` 전제("중간 발표 시 인증 하드코딩")와 일치하는 **문서화된 의도적 결정**이다.

## 변경 대상 파일

### 신규 — DB 스키마 문서
- `docs/db-schema.md` (신규 생성 — DB 변경이 포함되므로 필수)

### 신규 — 코드 (`bff-server/src/main/java/com/lina/bff/` 하위, 도메인 우선 패키징)
- `config/` — `WebConfig`(필요 시), `DemoSecurityConfig`, `RagClientConfig`(RestClient 빈), `CurrentUserProvider`/`FixedDemoUserProvider`
- `chat/controller/ConversationController.java`, `chat/controller/ChatController.java`
- `chat/service/ConversationService.java`, `chat/service/ChatService.java`
- `chat/repository/ConversationRepository.java`, `chat/repository/MessageRepository.java`
- `chat/entity/Conversation.java`, `chat/entity/Message.java`, `chat/entity/MessageSource.java`
- `chat/dto/` — `CreateConversationResponse`, `ConversationListResponse`, `ConversationSummaryResponse`, `UpdateConversationTitleRequest`, `UpdateConversationResponse`, `MessageHistoryResponse`, `MessageResponse`, `SourceResponse`, `ChatRequest`
- `rag/client/RagClient.java`, `rag/client/dto/RagQueryCommand.java`, `rag/client/dto/RagSseEvent.java`
- `feedback/controller/FeedbackController.java`, `feedback/service/FeedbackService.java`, `feedback/repository/FeedbackRepository.java`, `feedback/entity/Feedback.java`, `feedback/dto/CreateFeedbackRequest.java`, `feedback/dto/FeedbackResponse.java`
- `support/` — 공통 헬퍼 필요 시 (예: SSE 이벤트 직렬화 유틸)

### 신규 — 테스트 (`bff-server/src/test/java/com/lina/bff/` 하위)
- `chat/service/ConversationServiceTest.java`, `chat/service/ChatServiceTest.java`
- `chat/controller/ConversationControllerTest.java`, `chat/controller/ChatControllerTest.java` (MockMvc)
- `chat/repository/ConversationRepositoryTest.java`, `chat/repository/MessageRepositoryTest.java` (DataMongoTest, 임베디드 MongoDB)
- `rag/client/RagClientTest.java` (WireMock — SSE 응답 모킹)
- `feedback/service/FeedbackServiceTest.java`, `feedback/controller/FeedbackControllerTest.java`

### 변경 — 설정/빌드
- `bff-server/build.gradle` — `spring-boot-starter-data-mongodb` 추가, 테스트에 `de.flapdoodle.embed.mongo.spring30x`(임베디드 MongoDB), WireMock 추가
- `bff-server/src/main/resources/application.yml` — `spring.data.mongodb.uri`/`database`/`auto-index-creation`(환경변수 참조), `lina.demo.*` 추가
- `bff-server/src/main/resources/application-local.yml` — 로컬 MongoDB 접속 기본값(평문 secret 금지, `${...}` 참조)
- `bff-server/src/test/resources/application-test.yml` (신규) — 임베디드 MongoDB 7.x, `auto-index-creation: true`

### 문서
- `docs/db-schema.md` — 신규 작성 (필수)
- `docs/api-spec.md` — 구현이 명세와 일치하는지 검증, 불일치 시에만 수정 (현재 명세와 일치 예상 → 변경 최소)

## 수정하지 않을 파일

- `CLAUDE.md`, `backend/CLAUDE.md`, `backend/rules/*`
- `docs/architecture.md`, `docs/conventions.md`, `docs/ai/workflow.md`, `docs/ai/prompt-templates.md`
- `scripts/*.sh`, root `build.gradle`, `settings.gradle`, `gradle*`
- `common` 모듈 (응답/예외 포맷 그대로 사용. JWT Claim DTO는 3단계에서 추가)
- `auth-server` 모듈 전체
- `bff-server`의 `user/`, `admin/`, `security/` 패키지 (3·4단계 담당 영역)

## DB 스키마 설계 (MongoDB)

`docs/conventions.md` §11 (snake/camel 일관, 인덱스 목적 기록) 준수. `docs/db-schema.md`에 컬렉션 정의/샘플 문서/인덱스 목적을 작성한다.

| 컬렉션 | 핵심 필드 | 비고 |
|---|---|---|
| `conversations` | `_id`(=`conversationId` UUID), `userId`, `title`, `createdAt`, `updatedAt`, `lastMessageAt`, `isPinned`(기본 false), `deletedAt`(soft delete) | `userId`는 2단계에서 고정 데모 사용자. `isPinned`=채팅방 고정 |
| `messages` | `_id`(=`messageId` UUID), `conversationId`, `role`(user/assistant — lowercase, LLM/OpenAI 표준), `content`, `sources[]`(내장 배열), `confidenceScore`(nullable), `verificationResult`(nullable), `createdAt`, `deletedAt` | 질문·답변 모두 저장. 인용 출처는 별도 컬렉션 없이 `sources` 내장 (`domains.md` §1) |
| `feedbacks` | `_id`(=`feedbackId` UUID), `messageId`(UNIQUE), `rating`(LIKE/DISLIKE), `comment`(nullable), `createdAt` | 메시지 단위 피드백. QCA 연결은 `messageId`→assistant→직전 user 메시지로 추적 (`domains.md` §2) |

> MySQL 기반 4테이블 설계(`message_sources` 별도 컬렉션 포함)는 2026-05-20 자로 폐기. MySQL 은 3단계의 `users`(`role` 포함, §6.1)/`user_tokens`/`user_space_acl` 도입 시 재도입한다. 별도 `admins` 테이블 계획은 `users.role` 로 흡수(2026-06-02, `docs/db-schema.md` §6.1).

인덱스 (목적 함께 기록):
- `idx_conversations_user_active_recent { userId:1, deletedAt:1, isPinned:-1, lastMessageAt:-1 }` — 사용자별 활성 대화 고정 우선·최신순 페이징
- `idx_messages_conversation_active_created { conversationId:1, deletedAt:1, createdAt:1 }` — 메시지 이력 멀티턴 복원
- `uniq_feedbacks_message { messageId:1 } UNIQUE` — 메시지당 피드백 1건 강제 (재등록은 동일 문서 upsert)

> 설계 결정(확정): 대화/메시지 삭제는 soft delete(`deletedAt`)로 처리해 피드백·QCA 데이터를 보존한다.

## 구현 Feature 및 체크리스트

`docs/ai/workflow.md` §3 (AC → 실패 테스트 → 최소 구현 → 검증) 순서로 진행한다. Feature는 별도 세션/커밋으로 분리할 수 있으며 번호 순서대로 진행한다.

---

### Feature 1. DB 스키마 문서 & 영속 계층

#### 변경 대상 파일
- `docs/db-schema.md` (신규)
- `bff-server/build.gradle`, `bff-server/src/main/resources/application.yml`, `application-local.yml`, `src/test/resources/application-test.yml` (신규)
- `chat/entity/{Conversation,Message,MessageSource}.java`, `feedback/entity/Feedback.java`
- `chat/repository/{ConversationRepository,MessageRepository}.java`, `feedback/repository/FeedbackRepository.java`
- `chat/repository/{ConversationRepositoryTest,MessageRepositoryTest}.java`

#### 체크리스트
- [x] `docs/db-schema.md` 작성: `conversations`/`messages`(`sources` 내장)/`feedbacks` 컬렉션 정의, 인덱스 목적, soft delete·피드백 upsert 결정, 저장소 역할 분리 명시
- [x] `build.gradle`에 `spring-boot-starter-data-mongodb` 추가, 테스트에 임베디드 MongoDB(`de.flapdoodle.embed.mongo.spring30x`)·WireMock 추가
- [x] `application.yml`에 `spring.data.mongodb.uri`/`database`/`auto-index-creation` 환경변수 참조 + `lina.demo.*` 추가 (평문 secret 미포함)
- [x] `application-test.yml`: 임베디드 MongoDB 7.x, `auto-index-creation: true`
- [x] Document 3종 + 내장 값 객체 1종 작성 — `deletedAt` soft delete, `messages.sources[]` 내장 배열, 표준 주석 블록
- [x] Repository 작성 — 모든 조회에 `deletedAt == null` 필터, 데이터 액세스 주석(`conventions.md` §5.6)
- [x] `@DataMongoTest`(임베디드 MongoDB): 목록 페이징 정렬, 메시지 이력 정렬, soft delete 필터, 피드백 unique 검증, `sources` 내장 라운드트립 검증

> Feature 1 완료 (2026-05-19). 저장소 전환 재완료 (2026-05-20). `./scripts/verify.sh` 통과 — 직전 실행 결과를 본 항목 갱신 시 함께 기록.
> 부수 변경 (2026-05-19): JPA 도입으로 `BffApplicationTests` 프로파일을 `local`→`test`로 변경 (스모크 테스트가 실 DB 에 의존하지 않도록).
> 저장소 전환 (2026-05-20): 사용자 요청으로 MySQL/JPA → MongoDB/Spring Data MongoDB 로 재구현. `message_sources` 별도 컬렉션을 제거하고 `messages.sources` 내장 배열로 단순화. `backend/CLAUDE.md` §2.2/§6/§7 의 MongoDB 규칙을 BFF 영역에 한정해 CRUD 허용으로 갱신.

---

### Feature 2. 인증 부재 격리 + 데모 설정

#### 변경 대상 파일
- `config/CurrentUserProvider.java`, `config/FixedDemoUserProvider.java`, `config/DemoSecurityConfig.java`, `config/RagClientConfig.java`

#### 체크리스트
- [x] `CurrentUserProvider` 인터페이스 (`userId`, `groups` 반환) — 3단계 JWT 구현체로 교체 가능한 경계
- [x] `FixedDemoUserProvider` — `lina.demo.fixed-user-id`/`fixed-groups`/`fixed-space-key` 설정값 주입
- [x] `DemoSecurityConfig` — 전 경로 `permitAll` + CSRF 비활성, 표준 주석 + `NOTE:` 마커("중간발표 한정, 3단계에서 JWT 검증으로 대체")
- [x] `RagClientConfig` — 동기 `RestClient` 빈 (타임아웃 `lina.rag.*` 설정 주입, `Mono`/`Flux` 미사용)
- [x] production Controller/Service에 인증 비활성화 조건 분기 미추가 확인

> Feature 2 완료 (2026-05-21). `CurrentUserProvider` / `FixedDemoUserProvider` / `DemoSecurityConfig` / `RagClientConfig` 4종을 `bff-server/config/` 에 추가. `./scripts/verify.sh` 통과. Controller/Service 미수정 (Feature 3 이후 도입 시 본 추상화 사용).

---

### Feature 3. 대화 CRUD API

#### 변경 대상 파일
- `chat/controller/ConversationController.java`, `chat/service/ConversationService.java`
- `chat/dto/{CreateConversationResponse,ConversationListResponse,ConversationSummaryResponse,UpdateConversationRequest,UpdateConversationResponse}.java`
- `chat/service/ConversationServiceTest.java`, `chat/controller/ConversationControllerTest.java`

#### 체크리스트
- [x] `POST /api/conversations` — `conversationId`/`title`/`isPinned`(기본 false)/`createdAt` 반환, `ApiResponse` code 201
- [x] `GET /api/conversations` — 고정 데모 사용자 기준 고정 우선(`isPinned DESC`) → `last_message_at DESC` 페이징(page/size), 삭제 대화 제외
- [x] `PATCH /api/conversations/{conversationId}` — `title`/`isPinned` 부분 수정(둘 중 하나 이상 필수), `title`/`isPinned`/`updatedAt` 반환
- [x] `DELETE /api/conversations/{conversationId}` — soft delete, `data: null` 반환
- [x] `Conversation` 엔티티에 `isPinned`(기본 false) 필드 추가 + Repository 정렬 메서드를 `findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc` 로 변경 + 인덱스 `{userId,deletedAt,isPinned:-1,lastMessageAt:-1}` 갱신 (Feature 1 산출물 보강)
- [x] 존재하지 않거나 삭제된 대화 접근 시 `RESOURCE_NOT_FOUND`(404)
- [x] 필수 필드 누락/형식 오류는 공통 `ErrorResponse`(400)
- [x] **DTO 변환 시 모든 timestamp 를 KST(`Asia/Seoul`) `ZonedDateTime` 으로 직렬화** (확정된 결정 #6)
- [x] Service Unit Test (Repository Mock) + Controller MockMvc(정상/검증실패/404, Wrapper 구조·KST 표기 검증)

---

### Feature 4. 메시지 이력 조회 API

#### 변경 대상 파일
- `chat/controller/ConversationController.java`(엔드포인트 추가), `chat/service/ConversationService.java`
- `chat/dto/{MessageHistoryResponse,MessageResponse,SourceResponse}.java`

#### 체크리스트
- [x] `GET /api/conversations/{conversationId}/messages` — 멀티턴 복원용 전체 이력
- [x] Entity→DTO 변환 (Entity 직접 반환 금지), `role`(`user`/`assistant`, **lowercase** — LLM/OpenAI 표준)·인용 출처·`confidenceScore`·`verificationResult` 포함
- [x] `created_at ASC` 순서 보장, 삭제 메시지 제외
- [x] 없는/삭제 대화 시 404
- [x] **`createdAt`·출처 `sourceUpdatedAt` 모두 KST(`+09:00`) 표기로 직렬화** (확정된 결정 #6)
- [x] Service Unit Test + Controller MockMvc (출처 포함 응답 구조·KST 표기 검증)

---

### Feature 5. RAG Client + SSE 스트리밍 중계

#### 변경 대상 파일
- `rag/client/RagClient.java`, `rag/client/dto/{RagQueryCommand,RagSseEvent}.java`
- `chat/controller/ChatController.java`, `chat/service/ChatService.java`, `chat/dto/ChatRequest.java`
- `support/`(SSE 이벤트 직렬화 유틸, 필요 시)
- `rag/client/RagClientTest.java`, `chat/service/ChatServiceTest.java`, `chat/controller/ChatControllerTest.java`

#### 체크리스트
- [x] `RagClient` — `rag/client/`에서만 ML/AI Agent 호출, 동기 `RestClient`/`HttpClient` InputStream으로 SSE 파싱 (`Mono`/`Flux`/WebFlux 미사용)
- [x] ML 호출 시 다음을 전달 (`docs/api-spec.md` §2-1):
  - 질문(`question`), `conversationId`, 대화이력(`history`, 최근 N턴 — `lina.rag.history-turns` 기본 10). **`history[].role` 은 저장값 그대로**(`user`/`assistant` lowercase, LLM/OpenAI 표준 — boundary 변환 없음)
  - ACL: `userId`/`groups` (2단계 데모 고정값)
  - **spaceKey 등 스페이스 스코프 파라미터 미전달** (2026-06-04 결정 — LINA API 표면에서 spaceKey 제거). cross-space 검색이 유일한 모드, ACL(`userId`/`groups`) 만 적용. `lina.demo.fixed-space-key` 설정 deprecation 대상. (`api-spec.md` §2-1)
  - **`stream: true`** — BFF 는 항상 명시(토큰 스트리밍 ON, RAG 기본은 `false`)
  - **`/ml/query` 본문에 `accessToken`/`cloudId` 미포함** (확정된 결정 #5, 2026-05-22 갱신). 2026-06-05 갱신: `/ml/ingest`/RabbitMQ job payload 에도 Confluence credential set 을 포함하지 않는다. Data Ingestion Worker 가 `adminUserId` 로 auth-server 내부 API 에서 admin OAuth `accessToken` + `cloudId` 를 함께 조회한다.
- [x] **ACL fail-closed 게이트**: `userId` 가 비어 있거나 `groups` 가 빈 배열(`[]`)이면 `/ml/query` 호출을 **차단**하고 SSE `error`(`errorCode: UNAUTHORIZED`)로 종료 (`backend/CLAUDE.md` §6 "ACL 필터 없이 RAG 호출 금지" / `docs/api-spec.md` §2-1)
- [x] `POST /api/conversations/{conversationId}/chat` — `SseEmitter` 반환, Wrapper 미적용
- [x] `status`/`token`/`sources`/`verification`/`meta`/`done`/`error` 이벤트 중계 (집합 정본 `docs/api-spec.md` §1-1). `status`=진행표시, `meta`=호환용(제거 예정), 스트림은 `done`/`error`로 종료. 출처 `sourceUpdatedAt`·`done` 페이로드 timestamp 는 **KST 직렬화**
- [x] **Boundary 가공 (RAG → BFF → FE)**: (a) `error` 이벤트는 RAG·BFF·FE 모두 `{ "errorCode": ..., "message": ... }` 동일 키 — passthrough, `errorCode` 값이 §1-1 표 일치 여부만 검증; (b) `done` 은 RAG `{}` → BFF 가 저장한 assistant `messageId` 를 채워 `done: { "messageId": ... }` 로 중계 (`docs/api-spec.md` §2-1 / `backend/rules/rag-pipeline.md` §3)
- [x] user 메시지 선저장 → `done` 수신 시 assistant 메시지+출처+검증 저장 → `last_message_at` 갱신
- [ ] 첫 assistant 응답의 `meta.title` 로 대화 제목 **1회 자동 설정** (현재 `title` 이 기본 `"새 대화"` 일 때만; 이후·사용자 PATCH 수정 시 무시) — `docs/api-spec.md` §1-1 "대화 제목 자동 설정 규칙"
- [ ] ML 실패/타임아웃 시 재시도 없이 `error` 이벤트 전송 후 연결 정리 — SSE 타임아웃은 **idle 기준** `lina.rag.sse-timeout-ms`(기본 60s), 장시간 phase 는 `status` keep-alive, idle 초과 시 `error`(`ML_TIMEOUT`). 응답 헤더 `text/event-stream`/`no-cache`/`X-Accel-Buffering: no` (`docs/api-spec.md` §1-1)
- [ ] **PoC 토큰 보안 (Data Ingestion 호출 경로 한정)**: `accessToken` 을 로그·tracing 본문에 노출하지 않음 (마스킹), HTTP/RabbitMQ job·event payload 에 `accessToken`/`refreshToken`/`cloudId` 미포함, actuator 민감 endpoint 비노출 — `docs/api-spec.md` §2-2 보안 주의 준수 (RAG 질의 경로엔 토큰 자체가 없으므로 본 규칙 적용 대상은 ingestion 호출)
- [ ] WireMock 테스트: SSE 정상 스트림 / ML 5xx / 타임아웃 → `error` 이벤트·연결 정리, **요청 body 에 `accessToken`/`cloudId` 미포함** 검증 (`/ml/query` 본문에 토큰 누출 없음). 4단계 `/ml/ingest`/RabbitMQ job payload 테스트도 credential 미포함(`jobId`/`adminUserId`/`mode` 중심)으로 수행. 실제 ML 호출 금지.
- [ ] `ChatService` Service Unit Test (RagClient·Repository Mock), Controller 이벤트 시퀀스 검증

> **Mongo transaction 메모 (2026-06-07):** 현재 로컬/PoC Mongo 는 standalone 전제로 운용하므로 `MongoTransactionManager` 와 `@Transactional` 을 적용하지 않는다. standalone 에서 Mongo multi-document transaction 을 켜면 런타임 오류가 날 수 있다. 저장 책임은 `ChatMessagePersistenceService` 로 분리해 두었으므로, Mongo 를 replica set/sharded cluster 로 전환하고 `MongoTransactionManager` 를 설정한 뒤에는 긴 SSE 메서드가 아니라 `saveUserMessage` / `saveAssistantMessage` 같은 짧은 write 메서드에만 `@Transactional` 적용을 검토한다.

---

### Feature 6. 피드백 API

#### 변경 대상 파일
- `feedback/controller/FeedbackController.java`, `feedback/service/FeedbackService.java`
- `feedback/dto/{CreateFeedbackRequest,FeedbackResponse}.java`
- `feedback/service/FeedbackServiceTest.java`, `feedback/controller/FeedbackControllerTest.java`

#### 체크리스트
- [ ] `POST /api/messages/{messageId}/feedback` — `rating`(`LIKE`/`DISLIKE`, UPPER) 검증, `comment` 선택
- [ ] 메시지당 1건 unique + upsert: 신규 201 / 갱신 200
- [ ] 잘못된 `rating`/필수 누락 시 400, 없는 메시지 시 404
- [ ] **`createdAt` 응답을 KST(`+09:00`)로 직렬화** (확정된 결정 #6)
- [ ] Service Unit Test (신규/갱신 분기) + Controller MockMvc

---

### Feature 7. 대화 검색 (`GET /api/conversations/search`)

본인 대화의 메시지 본문(`messages.content`)에서 검색어 매칭. 결과는 대화 단위로 묶고 매칭 메시지 샘플 + 카운트 동반. (`docs/api-spec.md` §1-2 「대화 검색」, `docs/db-schema.md` §3.2)

#### 체크리스트
- [ ] `ConversationSearchController` — `GET /api/conversations/search` 진입점
- [ ] `q` 검증: **trim 후 길이 2~50 자**. 위반 시 `400` (`errorCode: INVALID_SEARCH_QUERY`). `size` 최대 50.
- [ ] `q` 의 정규식 메타문자 escape 후 `$regex` (case-insensitive) on `messages.content`. text index 는 후속 라운드 (`db-schema.md` §3.2 인덱스 표 후속 행)
- [ ] 권한 격리: `conversations.userId == 현재 사용자` 필터 강제 (2단계 데모 = `lina.demo.fixed-user-id`). `CurrentUserProvider` 통해서만 조회 — 타 사용자 대화 노출 차단
- [ ] soft delete 필터: `conversations.deletedAt == null` AND `messages.deletedAt == null`
- [ ] 정렬: `lastMessageAt` DESC (관련도 점수 미적용 — PoC)
- [ ] 응답 구조: `results[].matchedMessages` **대화당 최대 3개** + `matchCount` (총 매칭 수). `totalCount` 는 매칭 대화 총 수
- [ ] snippet 추출 유틸: 첫 매칭 위치 기준 좌우 ~40자, 본문 잘림 시 `...` prefix/suffix 부착. `matchPositions` 는 추출된 `snippet` 기준 `[[start, end]]` (end exclusive, UTF-16). **HTML 미생성** — XSS 방지
- [ ] `INVALID_SEARCH_QUERY` enum 값을 `common` 모듈 `ErrorCode` 에 추가 (도메인 특화 코드 최초 사례 — `docs/api-spec.md` Common 노트)
- [ ] Repository 테스트: 본인 대화만 매칭, 다른 사용자 대화 미노출, deleted 미노출, case-insensitive, 메타문자 escape
- [ ] Controller 테스트(MockMvc): `q` 미존재 / trim 길이 미달·초과 / `size` 초과 → 400 `INVALID_SEARCH_QUERY`

### Feature 8. 검증

#### 체크리스트
- [ ] `./scripts/format.sh` 성공
- [ ] `./scripts/lint.sh` 성공
- [ ] `./scripts/test.sh` 성공
- [ ] `./scripts/verify.sh` 성공
- [ ] `./gradlew :bff-server:bootRun` 기동 확인
- [ ] `docs/api-spec.md` §1 명세와 구현 정합성 확인 (불일치 시에만 수정)
- [ ] `git diff` 기준 의도하지 않은 변경 / 담당 외 파일 변경 없음
- [ ] `backend/CLAUDE.md` §7 체크리스트 점검 (`Mono`/`Flux` 미사용, MongoDB의 RAG 파이프라인 데이터 쓰기 없음, ACL 누락 경로 없음, 평문 secret 없음)

## 위험 요소

| 리스크 | 영향 | 완화책 |
|---|---|---|
| 데모용 `permitAll` SecurityConfig가 3단계 이후까지 잔존 | 보안 결함 | 표준 주석 + `NOTE:` 마커, 3단계 체크리스트에 "DemoSecurityConfig 제거" 명시, `CurrentUserProvider` 경계로 교체 비용 최소화 |
| WebFlux/`Mono`/`Flux`로 SSE 구현 유혹 | `backend/CLAUDE.md` §2.1/§6 위반 | `SseEmitter` + 동기 `RestClient`/`HttpClient` InputStream 파싱만 사용, 리뷰 체크 |
| ML 서버 미가동 상태의 데모 | 시연 실패 | RAG Client 타임아웃·`error` 이벤트 경로를 우선 견고화, WireMock 시나리오로 사전 검증 |
| 피드백 갱신 시 응답 코드 혼동 (201 vs 200) | 프론트 처리 영향 | 확정: 메시지당 1건 unique + upsert. 신규 201 / 갱신 200으로 명확히 분기, `api-spec.md` 정합성 확인 |
| soft delete 누락 필터로 삭제 데이터 노출 | 데이터 정합성 | 확정: soft delete. 모든 조회 쿼리에 `deletedAt == null` 강제, Repository 테스트로 검증 |
| `messages.content` 길이 (긴 답변) | 저장 실패 | MongoDB 문서당 16MB 한도 내 String 사용. 한도 초과 시 분할 저장 정책 필요 — `db-schema.md` §3.2 노트 참조 |
| MongoDB 운영 인스턴스 미가동 상태에서 부팅 | 컨텍스트 실패 가능 | 드라이버는 lazy 연결 — 부팅은 성공, 첫 호출 시 오류 발생. 헬스체크와 readiness 분리 |

## 문서 수정 필요 여부

- `docs/db-schema.md` — **필수 신규 작성** (DB 신규 도입)
- `docs/api-spec.md` — 구현·명세 정합성 확인. 현재 명세와 일치 예상이므로 변경은 최소(불일치 발견 시에만 수정)
- `docs/architecture.md` — 불필요 (정의된 계층/데이터 전략 준수)
- `docs/conventions.md`, `docs/ai/*` — 불필요

## 2단계 완료 기준 (Done Definition)

- [ ] 위 7개 도메인 Feature 의 외부 API 가 `docs/api-spec.md` §1 명세대로 동작 (대화 CRUD·메시지 이력·챗 SSE·피드백·대화 검색)
- [ ] `docs/db-schema.md` 작성 완료, Entity와 일치
- [ ] Service Unit / Controller MockMvc / Repository DataMongoTest / RagClient WireMock 테스트 통과
- [ ] `Mono`/`Flux`/WebFlux 미사용, MongoDB RAG 파이프라인 데이터(`raw_pages`/`chunked_units` 등) 쓰기 없음, ACL 누락 호출 경로 없음 (`backend/CLAUDE.md` §7 체크리스트)
- [ ] 인증 우회가 `DemoSecurityConfig`/`CurrentUserProvider`로 격리됨, production 분기 미추가
- [ ] 평문 secret 미포함 (DB 비밀번호 등 `${...}` 환경변수 참조)
- [ ] `./scripts/format.sh`/`lint.sh`/`test.sh`/`verify.sh` 통과
- [ ] `git diff` 기준 의도하지 않은 변경 없음, 담당 외 파일 미수정

## 확정된 결정 (사용자 확인 완료)

1. **피드백 재등록** (2026-05-19): 메시지당 1건 — `uniq_feedbacks_message (messageId)` 유니크 인덱스. 같은 메시지 재요청 시 동일 문서 upsert. 응답 코드는 신규 201 / 갱신 200으로 구분.
2. **삭제 방식** (2026-05-19): soft delete 확정. `conversations.deletedAt`, `messages.deletedAt` 사용. 조회는 모두 `deletedAt == null` 필터. 연결 피드백·QCA 데이터 보존.
3. **데모 사용자/스페이스** (2026-05-19): 설정값으로 분리하고 기본값은 임의 지정. `application.yml`에 `lina.demo.fixed-user-id: user-001`, `lina.demo.fixed-groups: Cloud-Control-Center`, `lina.demo.fixed-space-key: CPC` (모두 `${...}` 환경변수 오버라이드 가능). `db-schema.md`/구현에 위 결정 명시. **2026-06-04 갱신**: `fixed-space-key` 는 spaceKey API 표면 제거(api-spec v2.4.0)로 **deprecation 대상**. `FixedDemoUserProvider.getSpaceKey()`/주입 제거는 Feature 5 구현 시 일괄 정리(`CurrentUserProvider` 인터페이스에서도 제거).
4. **데이터 저장소** (2026-05-20): 2단계 BFF 도메인(대화·메시지·피드백) 영속은 **MongoDB** 사용. `message_sources`는 별도 컬렉션 없이 `messages.sources` 내장 배열로 단순화. MySQL 은 2단계에서는 미사용이며 3단계의 `users`(`role` 포함)/`user_tokens`/`user_space_acl` 도입 시 재도입(별도 `admins` 테이블 계획은 `users.role` 로 흡수 — 2026-06-02 갱신). `backend/CLAUDE.md` §2.2/§6/§7 의 MongoDB 쓰기 금지 규칙은 **RAG 파이프라인 데이터**(`raw_pages`/`raw_attachments`/`attachment_texts`/`chunked_units`/`import_jobs`/`sync_logs`)에 한정하도록 함께 갱신.
5. **Confluence OAuth 토큰/cloudId 조회 모드** (2026-05-21, 2026-05-22, 2026-06-05 갱신): **credential payload 미포함 모드로 정정** — BFF 는 Data Ingestion Pipeline 요청 본문 또는 RabbitMQ payload 에 `accessToken`/`refreshToken`/`cloudId` 를 첨부하지 않는다. Data Ingestion Worker 가 `adminUserId` 로 auth-server 내부 credential 조회 API 를 호출해 admin OAuth `accessToken` + `cloudId` 를 함께 조회한다(`docs/api-spec.md` §2-2). 운영 보호장치(로그·tracing 본문 마스킹, actuator 민감 endpoint 차단, NetworkPolicy, RabbitMQ payload credential 미포함) 동반 필수. 관련 설정 키: `lina.data-ingestion.*` (Data Ingestion 호출 클라이언트 base-url/timeout). 상세는 `backend/auth-server/current-plans.md` §3단계 Feature A~D 참조.
   - **2026-05-22 갱신 / 2026-06-05 정정**: 토큰 사용 대상을 RAG 질의(`/ml/query`, §2-1) → 데이터 수집(`/ml/ingest`, §2-2) 로 변경하되, 전달 방식은 payload 첨부가 아니라 Data Ingestion Worker 의 auth-server 내부 credential 조회로 정정. **근거**: 권한은 수집 시 Qdrant payload(`allowed_groups`/`allowed_users`) 에 ACL 저장 + 질의 시 JWT 의 `userId`/`groups` 로 필터링 (기획서 §6.4/§6.6). 따라서 `/ml/query` 는 라이브 Confluence 호출이 없어 토큰 불필요, 토큰은 크롤하는 수집 단계에서만 내부 조회해 사용. **전제**: "`/ml/query` 가 실시간 Confluence 호출을 일절 안 함" 을 가정 (※ ML 확인 대기 — 확인 후 본 결정 확정).
6. **시간 표기 정책** (2026-05-21): 저장은 UTC(`Instant`), **응답 JSON 의 모든 timestamp 는 KST(`+09:00`)** 로 절대 전환 — `instant.atZone(ZoneId.of("Asia/Seoul"))`. Feature 3/4/5(`done` 페이로드 및 출처)/6 응답 DTO 변환에 동일 정책 적용. `docs/api-spec.md` 모든 응답 예시도 이 표기로 일괄 갱신 완료.

---

# 4단계 — 부가 API (관리자 대시보드 · Confluence 미리보기)

> 진행 상태: 미착수. 6주차(관리자 대시보드)·5주차 이후(미리보기) 진입 시 본 섹션을 풀세트 Feature(체크리스트·변경 대상 파일·테스트)로 확장한다.
> 상세 계약: `docs/api-spec.md` §4-2 / §4-3, 데이터 소스: `backend/rules/domains.md` §4, 화면: 기획서 §5.2 ADMIN-01.

## 추적 대상 엔드포인트

### 관리자 대시보드 (6주차, ADMIN 전용)

| 엔드포인트 | 응답 핵심 | 데이터 소스 | 기획서 |
|---|---|---|---|
| `GET /api/admin/stats` | 일간 질의 수·평균 응답 시간·시간대별 접속 추이 | MySQL | §6.7 사용 추이 |
| `GET /api/admin/users` | 일일/전체 사용자 + 사용자별 ACL 카운트(접근 스페이스/페이지/첨부) | MySQL | §6.7 인원 관리 |
| `GET /api/admin/data` | 스페이스/페이지/`vectorDbSize`·`lastSyncAt` | MongoDB (읽기) | §6.7 데이터 관리 |
| `GET /api/admin/feedback` | 긍정/부정 비율·추이·부정 원문(QCA 매핑) | MySQL | §6.7 피드백 관리 |
| `GET /api/admin/sync` | 동기화 이력(`syncId`/`status`/`updatedPages`/...) | MongoDB (읽기) | §6.7 데이터 관리 |

공통:
- `/api/admin/*` **ADMIN 전용** — 미인증 `401(UNAUTHORIZED)`, 일반 사용자 `403(FORBIDDEN)` (`docs/api-spec.md` §4-2 / §1-4).
- 공통 쿼리 파라미터(제안 — 6주차 확정): `period`(daily/hourly), `from`/`to`(KST), `page`/`size`.
- 응답은 공통 Wrapper(`ApiResponse`), 시간은 **KST 직렬화**.
- QCA 매핑: assistant `messageId` → 직전 `user` 메시지 (`backend/rules/domains.md` §2).

### 데이터 수집 트리거 (관리자용, ADMIN 전용)

| 엔드포인트 | 응답 핵심 | 대상 | 비고 |
|---|---|---|---|
| `POST /api/admin/key/activate` | `activatedUntil` (60분 후) | auth-server 내부 API → Atlassian `POST /api/v2/admin-key` | **수동/테스트용** — 일반 사용 경로는 `/api/admin/ingest` 가 자동 처리(아래). 검증·디버깅·운영 점검 때만 호출. (`docs/api-spec.md` §1-4 / `docs/adr/0001-page-level-acl-source.md` §2.1) |
| `POST /api/admin/ingest` | `jobId`/`status`(`STARTED`)/`startedAt` | RabbitMQ ingest job 또는 ML 서버 `POST /ml/ingest` | body `{ mode }` (생략 시 `"full"`). **스페이스 스코프 없음** — admin Key 로 admin 이 접근 가능한 모든 스페이스 일괄 크롤(2026-06-04 결정). **내부 처리(2026-06-05 갱신 — watcher polling 대체)**: ① BFF 가 admin 의 Admin Key 활성 상태 확인 → 만료/미활성이면 auth-server 통해 자동 activate (회의 결정 2026-06-02) ② BFF 가 `jobId` 생성 후 RabbitMQ ingest job 을 직접 발행하거나 Data Ingestion Pipeline 의 `/ml/ingest` 를 호출해 job 발행 위임 ③ job payload 는 `jobId`/`adminUserId`/`mode` 등 식별·상태 정보만 포함, `accessToken`/`refreshToken`/`cloudId` 미포함 ④ Data Ingestion Worker 가 `adminUserId` 로 auth-server 내부 credential 조회 API 에서 `accessToken`+`cloudId` 를 함께 조회 ⑤ Data Ingestion 이 Atlassian REST 호출 시 `Authorization: Bearer {admin accessToken}` + `Atl-Confluence-With-Admin-Key: true` 헤더 부여 ⑥ Data Ingestion Pipeline 이 RabbitMQ completion event 발행 ⑦ BFF consumer 가 completion event 를 consume하고 auth-server `POST /internal/admin/key/deactivate` 호출 → Atlassian Admin Key 폐기. 2단계 demo 데이터 셋업도 본 endpoint 사용. (`docs/api-spec.md` §1-4·§2-2) |
| `GET /api/admin/ingest/status/{jobId}` | `jobId`/`status`/`totalPages`/`processedPages`/`failedPages`/`startedAt` | ML 서버 `GET /ml/ingest/status/{jobId}` | passthrough. `status`: `STARTED`/`IN_PROGRESS`/`COMPLETED`/`FAILED` |

공통: `/api/admin/*` ADMIN 전용(미인증 401·일반 403). PoC 토큰 보안(평문 로그 노출 금지·HTTP/RabbitMQ payload credential 미포함·NetworkPolicy) — `docs/api-spec.md` §2-2 보안 주의 참조. **검증 게이트**: OAuth Bearer + Admin Key 헤더 동작은 3단계 auth-server 구현 직후 curl 로 확인(`backend/auth-server/current-plans.md` Feature A 게이트 항목).

### Feature 1. RabbitMQ completion event / Admin Key deactivate

목표: `/api/admin/ingest` 이후 Admin Key 말소를 polling watcher 가 아니라 RabbitMQ completion event consumer 로 처리한다. completion event 는 BFF consumer 가 담당하고, 실제 Atlassian Admin Key 말소는 auth-server 내부 deactivate API 로 위임한다.

Credential 처리 원칙:
- 기존 2026-06-04 흐름의 `/ml/ingest` 본문 직접 전달(`accessToken` + `cloudId`)은 사용하지 않는다. watcher polling 은 원래 credential 전달 수단이 아니라 완료 감지/deactivate 트리거였으며, 이 책임만 completion event 로 대체한다.
- BFF 는 `/api/admin/ingest` 처리 중 `accessToken`/`refreshToken`/`cloudId` 를 조회하거나 Data Ingestion Pipeline/RabbitMQ payload 에 전달하지 않는다.
- BFF 또는 Data Ingestion Pipeline 이 발행하는 ingest job payload 는 `jobId`, `adminUserId`, `mode`, `requestedAt` 등 식별 정보만 포함한다.
- Data Ingestion Worker 는 job consume 후 `adminUserId` 로 auth-server 내부 credential 조회 API 를 호출해 admin OAuth `accessToken` + `cloudId` 를 함께 받는다.
- Data Ingestion Worker 는 Confluence 호출 시 `Authorization: Bearer {admin accessToken}` + `Atl-Confluence-With-Admin-Key: true` 헤더를 사용한다.

- [ ] BFF ingest 요청 처리에서 `jobId` 생성, Admin Key activate, RabbitMQ ingest job 발행 또는 Data Ingestion `/ml/ingest` 호출 위임 경계를 확정
- [ ] ingest job payload schema 정의: `jobId`, `adminUserId`, `mode`, `requestedAt` 중심. `accessToken`/`refreshToken`/`cloudId` 포함 금지
- [ ] Data Ingestion Worker 가 `adminUserId` 로 auth-server 내부 credential 조회 API 에서 `accessToken` + `cloudId` 를 함께 조회하는 계약 확인
- [ ] completion event consumer 구현 계획 수립: `jobId`, `adminUserId`, `mode`, `status`, `completedAt`, `errorCode`, `message` 수신 후 auth-server Admin Key deactivate 내부 API 호출
- [ ] `jobId` 기준 중복 completion event idempotency 정책 문서화 및 테스트 항목 추가
- [ ] BFF 재시작/consumer 장애 시 RabbitMQ durable queue 에 남은 completion event 재처리 정책 문서화
- [ ] deactivate 실패 재시도 초안: backoff 적용, 최대 5회 재시도 후 DLQ 이동
- [ ] DLQ 이동 조건 문서화: payload schema 오류, `jobId`/`adminUserId` 누락, deactivate 5회 실패, 복구 불가능한 auth-server 4xx
- [ ] DLQ 수동 복구 절차 문서화: 원인 조치 → auth-server deactivate 수동 호출 또는 event 재발행 → 성공 확인 후 DLQ event 폐기
- [ ] Admin Key 60분 TTL 은 consumer/DLQ 복구 실패 시 최종 fallback 임을 운영 문서에 명시

### Confluence 페이지 미리보기 (5주차 이후 — 인증 의존)

| 엔드포인트 | 응답 핵심 | 데이터 소스 |
|---|---|---|
| `GET /api/confluence/pages/preview?pageId=...` | `bodyViewValue`(HTML)·`breadcrumbs`·`pageUrl`·메타데이터 | Confluence REST (서버 보관 OAuth 토큰) |

공통:
- 인증 필수(Bearer JWT). 미인증 `401`.
- FE 는 `bodyViewValue` 를 DOMPurify 로 sanitize 후 `v-html` 렌더링.
- **TBD (3단계 결정)**: 호출 주체 — (a) BFF 가 토큰 사용해 직접 Confluence 호출 vs (b) Authorization Server 프록시 (`docs/api-spec.md` §4-3 TBD, `backend/CLAUDE.md` §6).

## 진입 주차에서 정의할 것

- Feature 단위 분할(엔드포인트별 또는 영역별 — 관리자 통계/사용자/데이터/피드백/동기화, 미리보기 1건). Admin Key completion event 처리는 4단계 Feature 1 로 우선 추적
- `admin/controller`·`admin/service` 또는 `dashboard/...` 등 패키지/파일 구조
- ADMIN 권한 가드(`@PreAuthorize` 또는 Spring Security Filter) 설계
- WireMock/MockMvc 테스트 시나리오(권한 분기·기간 필터·페이지네이션)
- 6주차 쿼리 파라미터 확정 (제안값 `period`/`from`/`to`/`page`/`size` 검증)
