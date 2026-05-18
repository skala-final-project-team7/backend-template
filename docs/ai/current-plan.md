# Current Plan

> 현재 진행 중인 Backend 작업 계획. 세션 간 인수인계용 (`docs/ai/workflow.md` §6.3).
> 1단계 완료 후 2단계 계획을 이어 작성한다.
> 2026-05-15: 중간발표 대비로 2단계(BFF Server) ↔ 3단계(Authorization Server) 순서 변경.

---

## 진행 상태 요약

| 단계 | 범위 | 상태 |
|---|---|---|
| 1단계 | 프로젝트 초기 셋업 (패키지 구조, 설정, 공통 예외/응답) | ✅ 완료 (2026-05-15) |
| 2단계 | BFF Server 핵심 API (대화, RAG 호출, SSE 중계) | 미착수 |
| 3단계 | Authorization Server (Confluence OAuth 2.0, JWT 발급) | 미착수 |
| 4단계 | 부가 API (피드백, 관리자 대시보드) | 미착수 |

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

## 다음 단계 예고

> **순서 변경 (2026-05-15):** 중간발표를 위해 데모 가능한 BFF Server를 먼저 구현한다.
> 기존 2단계(Authorization Server) ↔ 3단계(BFF Server) 순서를 맞바꿨다.
> 아래 **§순서 변경 시 주의사항**의 JWT 계약 선고정 작업을 2단계 착수 전에 먼저 수행한다.

### 2단계 — BFF Server 핵심 API
- JWT 검증 필터 (auth-server가 발급할 JWT의 서명/만료/Claim 검증)
- 대화 생성/조회 API
- RAG Pipeline Client (HTTP)
- SSE 스트리밍 중계 (`SseEmitter`)
- 인증은 Test Security Config / `@WithMockUser` / 테스트 키페어로 발급한 테스트 JWT로 검증
- `backend/rules/domains.md`, `backend/rules/rag-pipeline.md`, `backend/rules/auth.md` 참조

### 3단계 — Authorization Server
- Confluence OAuth 2.0 Authorization Code Flow 구현
- Access/Refresh Token MySQL 암호화 저장
- JWT 발급 (`user_id`, `groups` Claim) — 2단계에서 고정한 JWT 계약 준수
- Refresh Token 기반 자동 갱신
- `backend/rules/auth.md` 참조

### 4단계 — 부가 API
- 피드백 저장/집계 API
- 관리자 대시보드 API (인원/데이터/사용 추이/피드백)
- `backend/rules/domains.md` §2, §4 참조

---

## 순서 변경 시 주의사항 (2 ↔ 3 swap)

### 결론
BFF Server를 Authorization Server보다 먼저 구현해도 **기술적으로 문제없다.**
BFF의 핵심 기능(대화 CRUD, RAG 호출, SSE 중계)은 auth-server 런타임에 의존하지 않고,
인증 부분만 테스트용 JWT로 검증하면 독립적으로 개발·테스트가 가능하다.

### 단, 선행 조건 1개 — "JWT 계약 선고정"
BFF의 JWT 검증 필터와 auth-server의 JWT 발급기는 **동일한 JWT 계약**을 공유해야 한다.
이를 2단계 착수 전에 먼저 확정하지 않으면, 3단계에서 auth-server가 다른 형식으로 발급해
BFF 검증 로직을 재작성해야 하는 재작업 리스크가 생긴다.

2단계 착수 전 다음을 먼저 고정한다 (산출물: `docs/api-spec.md` 인증 섹션 초안):

- 서명 알고리즘 (예: RS256) 및 키 형식 (공개키/개인키 PEM)
- 필수 Claim: `user_id`, `groups`, `iss`, `exp`, `iat` (`backend/rules/auth.md` §2)
- 토큰 전달 방식: HttpOnly Cookie (`backend/rules/auth.md` §3 — Body 금지)
- (선택) `common` 모듈에 JWT Claim DTO를 두어 2·3단계가 공유

### 잔여 리스크와 완화책

| 리스크 | 영향 | 완화책 |
|---|---|---|
| 실제 Confluence OAuth → JWT → BFF E2E 흐름 검증이 3단계로 지연 | 통합 버그 발견이 늦어짐 | JWT 계약을 코드(공유 DTO)로 고정 + 계약 기반 테스트를 2단계에 작성 |
| BFF가 가정한 JWT 형식과 auth-server 구현 불일치 | 재작업 | 위 "JWT 계약 선고정" 산출물을 단일 기준으로 사용 |
| 테스트용 인증 우회 코드가 production에 유입 | 보안 결함 | `backend/rules/auth.md` §3 — production 코드에 인증 분기 추가 금지. Test Security Config / `@WithMockUser`만 사용 |
| 실제 ACL(`groups`) 데이터 부재로 RAG 호출 ACL 검증 미흡 | 데모 시 권한 필터 미검증 | 테스트 JWT에 대표 `groups` 값을 넣어 ACL 전달 경로까지 검증 |

### 중간발표 관점 이점
대화 생성 → RAG 호출 → SSE 스트리밍 답변은 시연 가치가 높다.
auth-server(로그인 화면 없는 토큰 발급 서버)보다 데모 임팩트가 크므로 순서 변경이 합리적이다.
