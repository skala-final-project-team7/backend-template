# Current Plan

> 현재 진행 중인 Backend 작업 계획. 세션 간 인수인계용 (`docs/ai/workflow.md` §6.3).
> 1단계 완료 후 2단계 계획을 이어 작성한다.
> 2026-05-15: 중간발표 대비로 2단계(BFF Server) ↔ 3단계(Authorization Server) 순서 변경.

---

## 진행 상태 요약

| 단계 | 범위 | 상태 |
|---|---|---|
| 1단계 | 프로젝트 초기 셋업 (패키지 구조, 설정, 공통 예외/응답) | ✅ 완료 (2026-05-15) |
| 2단계 | BFF Server 핵심 API (대화 CRUD, RAG 호출 + SSE 중계, 메시지 이력, 피드백, DB 스키마) | 📝 Plan 작성 완료 (착수 전, 2026-05-19) |
| 3단계 | Authorization Server (Confluence OAuth 2.0, JWT 발급) + BFF JWT 검증 필터 | 미착수 |
| 4단계 | 부가 API (관리자 대시보드) | 미착수 |

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
| 대화 제목 수정 | PATCH | `/api/conversations/{conversationId}` |
| 대화 삭제 | DELETE | `/api/conversations/{conversationId}` |
| 챗봇 질의 (SSE 중계) | POST | `/api/conversations/{conversationId}/chat` |
| 피드백 등록 | POST | `/api/messages/{messageId}/feedback` |

제외 (명시적): JWT 검증/OAuth(3단계), 관리자 대시보드 API(4단계), `/api/admin/ingest`(4단계), MongoDB 접근, RabbitMQ 발행.

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
- `chat/repository/ConversationRepositoryTest.java`, `chat/repository/MessageRepositoryTest.java` (DataJpaTest, H2)
- `rag/client/RagClientTest.java` (WireMock — SSE 응답 모킹)
- `feedback/service/FeedbackServiceTest.java`, `feedback/controller/FeedbackControllerTest.java`

### 변경 — 설정/빌드
- `bff-server/build.gradle` — `spring-boot-starter-data-jpa`, `mysql-connector-j`(runtimeOnly) 추가, 테스트에 `com.h2database:h2`, WireMock 추가
- `bff-server/src/main/resources/application.yml` — `spring.datasource`/`spring.jpa` 기본값(환경변수 참조), `lina.demo.*` 추가
- `bff-server/src/main/resources/application-local.yml` — 로컬 MySQL 접속 기본값(평문 secret 금지, `${...}` 참조)
- `bff-server/src/test/resources/application-test.yml` (신규) — H2, JPA `ddl-auto: create-drop`

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

## DB 스키마 설계 (MySQL)

`docs/conventions.md` §11 (snake_case, 복수형 테이블, FK 문서화, 인덱스 목적 기록) 준수. `docs/db-schema.md`에 ERD/DDL/인덱스 목적을 작성한다.

| 테이블 | 핵심 컬럼 | 비고 |
|---|---|---|
| `conversations` | `conversation_id`(PK, UUID), `user_id`, `title`, `created_at`, `updated_at`, `last_message_at`, `deleted_at`(soft delete) | `user_id`는 2단계에서 고정 데모 사용자 |
| `messages` | `message_id`(PK, UUID), `conversation_id`(FK), `role`(user/assistant), `content`, `confidence_score`(nullable), `verification_result`(nullable), `created_at` | 질문·답변 모두 저장 (`domains.md` §1) |
| `message_sources` | `source_id`(PK), `message_id`(FK), `title`, `page_id`, `space_id`, `space_name`, `url`, `source_updated_at`, `relevance_score` | assistant 메시지의 인용 출처 (`domains.md` §1) |
| `feedbacks` | `feedback_id`(PK, UUID), `message_id`(FK), `rating`(like/dislike), `comment`(nullable), `created_at` | 메시지 단위 피드백. QCA 연결은 `message_id`→assistant→직전 user 메시지로 추적 (`domains.md` §2) |

인덱스 (목적 함께 기록):
- `idx_conversations_user_last_msg (user_id, last_message_at DESC, deleted_at)` — 대화 목록 페이징 조회
- `idx_messages_conversation_created (conversation_id, created_at ASC)` — 메시지 이력 멀티턴 복원
- `idx_message_sources_message (message_id)` — 메시지별 출처 fetch
- `uniq_feedbacks_message (message_id)` — 메시지당 피드백 1건 (재등록 시 정책: **확정 필요** — 아래 위험 요소 참조)

> 설계 결정 후보(문서에 명시): 대화/메시지 삭제는 soft delete(`deleted_at`)로 처리해 피드백·QCA 데이터를 보존한다. 하드 삭제가 요구되면 변경.

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
- [x] `docs/db-schema.md` 작성: `conversations`/`messages`/`message_sources`/`feedbacks` DDL, FK 관계, 인덱스 목적, soft delete·피드백 upsert 결정 명시
- [x] `build.gradle`에 `spring-boot-starter-data-jpa`, `mysql-connector-j`(runtimeOnly), test `com.h2database:h2`·WireMock 추가
- [x] `application.yml`에 `spring.datasource`/`spring.jpa` 환경변수 참조 + `lina.demo.*` 추가 (평문 secret 미포함)
- [x] `application-test.yml`: H2, `ddl-auto: create-drop`
- [x] Entity 4종 작성 — `deleted_at` soft delete, `content` `@Lob`(MySQL LONGTEXT), 표준 주석 블록
- [x] Repository 작성 — 모든 조회에 `deleted_at IS NULL` 필터, 데이터 액세스 주석(`conventions.md` §5.6)
- [x] `@DataJpaTest`(H2): 목록 페이징 정렬, 메시지 이력 정렬, soft delete 필터, 피드백 unique 검증

> Feature 1 완료 (2026-05-19). `./scripts/verify.sh` 통과 (7 tests, 0 failures).
> 부수 변경: JPA 도입으로 `BffApplicationTests` 프로파일을 `local`→`test`(H2)로 변경 (스모크 테스트가 실 MySQL에 의존하지 않도록, `backend/rules/testing.md` 준수).
> 인덱스 컬럼 순서: 초안의 `(user_id, last_message_at, deleted_at)`→`(user_id, deleted_at, last_message_at)`로 필터 선택도 개선 (목적 동일, `docs/db-schema.md` §3.1에 근거 기록).

---

### Feature 2. 인증 부재 격리 + 데모 설정

#### 변경 대상 파일
- `config/CurrentUserProvider.java`, `config/FixedDemoUserProvider.java`, `config/DemoSecurityConfig.java`, `config/RagClientConfig.java`

#### 체크리스트
- [ ] `CurrentUserProvider` 인터페이스 (`userId`, `groups` 반환) — 3단계 JWT 구현체로 교체 가능한 경계
- [ ] `FixedDemoUserProvider` — `lina.demo.fixed-user-id`/`fixed-groups`/`fixed-space-key` 설정값 주입
- [ ] `DemoSecurityConfig` — 전 경로 `permitAll` + CSRF 비활성, 표준 주석 + `NOTE:` 마커("중간발표 한정, 3단계에서 JWT 검증으로 대체")
- [ ] `RagClientConfig` — 동기 `RestClient` 빈 (타임아웃 `lina.rag.*` 설정 주입, `Mono`/`Flux` 미사용)
- [ ] production Controller/Service에 인증 비활성화 조건 분기 미추가 확인

---

### Feature 3. 대화 CRUD API

#### 변경 대상 파일
- `chat/controller/ConversationController.java`, `chat/service/ConversationService.java`
- `chat/dto/{CreateConversationResponse,ConversationListResponse,ConversationSummaryResponse,UpdateConversationTitleRequest,UpdateConversationResponse}.java`
- `chat/service/ConversationServiceTest.java`, `chat/controller/ConversationControllerTest.java`

#### 체크리스트
- [ ] `POST /api/conversations` — `conversationId`/`title`/`createdAt` 반환, `ApiResponse` code 201
- [ ] `GET /api/conversations` — 고정 데모 사용자 기준 `last_message_at DESC` 페이징(page/size), 삭제 대화 제외
- [ ] `PATCH /api/conversations/{conversationId}` — 제목 수정, `updatedAt` 반환
- [ ] `DELETE /api/conversations/{conversationId}` — soft delete, `data: null` 반환
- [ ] 존재하지 않거나 삭제된 대화 접근 시 `RESOURCE_NOT_FOUND`(404)
- [ ] 필수 필드 누락/형식 오류는 공통 `ErrorResponse`(400)
- [ ] Service Unit Test (Repository Mock) + Controller MockMvc(정상/검증실패/404, Wrapper 구조 검증)

---

### Feature 4. 메시지 이력 조회 API

#### 변경 대상 파일
- `chat/controller/ConversationController.java`(엔드포인트 추가), `chat/service/ConversationService.java`
- `chat/dto/{MessageHistoryResponse,MessageResponse,SourceResponse}.java`

#### 체크리스트
- [ ] `GET /api/conversations/{conversationId}/messages` — 멀티턴 복원용 전체 이력
- [ ] Entity→DTO 변환 (Entity 직접 반환 금지), 인용 출처·`confidenceScore`·`verificationResult` 포함
- [ ] `created_at ASC` 순서 보장, 삭제 메시지 제외
- [ ] 없는/삭제 대화 시 404
- [ ] Service Unit Test + Controller MockMvc (출처 포함 응답 구조 검증)

---

### Feature 5. RAG Client + SSE 스트리밍 중계

#### 변경 대상 파일
- `rag/client/RagClient.java`, `rag/client/dto/{RagQueryCommand,RagSseEvent}.java`
- `chat/controller/ChatController.java`, `chat/service/ChatService.java`, `chat/dto/ChatRequest.java`
- `support/`(SSE 이벤트 직렬화 유틸, 필요 시)
- `rag/client/RagClientTest.java`, `chat/service/ChatServiceTest.java`, `chat/controller/ChatControllerTest.java`

#### 체크리스트
- [ ] `RagClient` — `rag/client/`에서만 ML 호출, 동기 `RestClient`/`HttpClient` InputStream으로 SSE 파싱 (`Mono`/`Flux`/WebFlux 미사용)
- [ ] ML 호출 시 질문·대화이력(최근 N턴, `lina.rag.history-turns` 기본 10)·ACL(`userId`/`groups`)·`conversationId` 전달 (ACL 누락 경로 없음)
- [ ] `POST /api/conversations/{conversationId}/chat` — `SseEmitter` 반환, Wrapper 미적용
- [ ] `token`/`sources`/`verification`/`done` 이벤트 순서대로 중계
- [ ] user 메시지 선저장 → `done` 수신 시 assistant 메시지+출처+검증 저장 → `last_message_at` 갱신
- [ ] ML 실패/타임아웃 시 재시도 없이 `error` 이벤트 전송 후 연결 정리 (SSE 타임아웃 `lina.rag.sse-timeout-ms`)
- [ ] WireMock 테스트: SSE 정상 스트림 / ML 5xx / 타임아웃 → `error` 이벤트·연결 정리 (실제 ML 호출 금지)
- [ ] `ChatService` Service Unit Test (RagClient·Repository Mock), Controller 이벤트 시퀀스 검증

---

### Feature 6. 피드백 API

#### 변경 대상 파일
- `feedback/controller/FeedbackController.java`, `feedback/service/FeedbackService.java`
- `feedback/dto/{CreateFeedbackRequest,FeedbackResponse}.java`
- `feedback/service/FeedbackServiceTest.java`, `feedback/controller/FeedbackControllerTest.java`

#### 체크리스트
- [ ] `POST /api/messages/{messageId}/feedback` — `rating`(like/dislike) 검증, `comment` 선택
- [ ] 메시지당 1건 unique + upsert: 신규 201 / 갱신 200
- [ ] 잘못된 `rating`/필수 누락 시 400, 없는 메시지 시 404
- [ ] Service Unit Test (신규/갱신 분기) + Controller MockMvc

---

### Feature 7. 검증

#### 체크리스트
- [ ] `./scripts/format.sh` 성공
- [ ] `./scripts/lint.sh` 성공
- [ ] `./scripts/test.sh` 성공
- [ ] `./scripts/verify.sh` 성공
- [ ] `./gradlew :bff-server:bootRun` 기동 확인
- [ ] `docs/api-spec.md` §1 명세와 구현 정합성 확인 (불일치 시에만 수정)
- [ ] `git diff` 기준 의도하지 않은 변경 / 담당 외 파일 변경 없음
- [ ] `backend/CLAUDE.md` §7 체크리스트 점검 (`Mono`/`Flux` 미사용, MongoDB 쓰기 없음, ACL 누락 경로 없음, 평문 secret 없음)

## 위험 요소

| 리스크 | 영향 | 완화책 |
|---|---|---|
| 데모용 `permitAll` SecurityConfig가 3단계 이후까지 잔존 | 보안 결함 | 표준 주석 + `NOTE:` 마커, 3단계 체크리스트에 "DemoSecurityConfig 제거" 명시, `CurrentUserProvider` 경계로 교체 비용 최소화 |
| WebFlux/`Mono`/`Flux`로 SSE 구현 유혹 | `backend/CLAUDE.md` §2.1/§6 위반 | `SseEmitter` + 동기 `RestClient`/`HttpClient` InputStream 파싱만 사용, 리뷰 체크 |
| ML 서버 미가동 상태의 데모 | 시연 실패 | RAG Client 타임아웃·`error` 이벤트 경로를 우선 견고화, WireMock 시나리오로 사전 검증 |
| 피드백 갱신 시 응답 코드 혼동 (201 vs 200) | 프론트 처리 영향 | 확정: 메시지당 1건 unique + upsert. 신규 201 / 갱신 200으로 명확히 분기, `api-spec.md` 정합성 확인 |
| soft delete 누락 필터로 삭제 데이터 노출 | 데이터 정합성 | 확정: soft delete. 모든 조회 쿼리에 `deleted_at IS NULL` 강제, Repository 테스트로 검증 |
| `messages.content` 길이 (긴 답변) | 저장 실패 | `TEXT`/`LONGTEXT` 사용, `db-schema.md`에 명시 |

## 문서 수정 필요 여부

- `docs/db-schema.md` — **필수 신규 작성** (DB 신규 도입)
- `docs/api-spec.md` — 구현·명세 정합성 확인. 현재 명세와 일치 예상이므로 변경은 최소(불일치 발견 시에만 수정)
- `docs/architecture.md` — 불필요 (정의된 계층/데이터 전략 준수)
- `docs/conventions.md`, `docs/ai/*` — 불필요

## 2단계 완료 기준 (Done Definition)

- [ ] 위 7개 외부 API가 `docs/api-spec.md` §1 명세대로 동작
- [ ] `docs/db-schema.md` 작성 완료, Entity와 일치
- [ ] Service Unit / Controller MockMvc / Repository DataJpaTest / RagClient WireMock 테스트 통과
- [ ] `Mono`/`Flux`/WebFlux 미사용, MongoDB 쓰기 없음, ACL 누락 호출 경로 없음 (`backend/CLAUDE.md` §7 체크리스트)
- [ ] 인증 우회가 `DemoSecurityConfig`/`CurrentUserProvider`로 격리됨, production 분기 미추가
- [ ] 평문 secret 미포함 (DB 비밀번호 등 `${...}` 환경변수 참조)
- [ ] `./scripts/format.sh`/`lint.sh`/`test.sh`/`verify.sh` 통과
- [ ] `git diff` 기준 의도하지 않은 변경 없음, 담당 외 파일 미수정

## 확정된 결정 (2026-05-19, 사용자 확인 완료)

1. **피드백 재등록**: 메시지당 1건 — `uniq_feedbacks_message (message_id)` 적용. 같은 메시지 재요청 시 기존 row 갱신(upsert). 응답 코드는 신규 201 / 갱신 200으로 구분.
2. **삭제 방식**: soft delete 확정. `conversations.deleted_at`, `messages.deleted_at` 사용. 조회는 모두 `deleted_at IS NULL` 필터. 연결 피드백·QCA 데이터 보존.
3. **데모 사용자/스페이스**: 설정값으로 분리하고 기본값은 임의 지정. `application.yml`에 `lina.demo.fixed-user-id: user-001`, `lina.demo.fixed-groups: Cloud-Control-Center`, `lina.demo.fixed-space-key: CPC` (모두 `${...}` 환경변수 오버라이드 가능). `db-schema.md`/구현에 위 결정 명시.
