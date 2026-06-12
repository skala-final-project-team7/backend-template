# auth-server Current Plans

> `auth-server` 모듈의 현재 진행 중인 Plan. 세션 간 인수인계용 (`docs/ai/workflow.md` §6.3).
> 모듈 전용 규칙은 `backend/auth-server/CLAUDE.md` 참조.
> 2026-06-09: 3단계 Plan 을 bff-server 형식(Feature 단위 변경 대상 파일 + 체크리스트)으로 재작성. 기존 Feature A(11개 항목 묶음)를 Feature 0~8 로 분할하고, 미해결 결정을 **§착수 전 결정 필요**로 분리.

---

## 진행 상태 요약

| 단계 | 범위 | 상태 |
|---|---|---|
| 1단계 | 프로젝트 초기 셋업 (패키지 구조, 설정, 공통 응답/예외) | ✅ 완료 (2026-05-15, `backend/bff-server/current-plans.md` 1단계 공동 작업) |
| 3단계 | Confluence OAuth 2.0 Authorization Code Flow + Access/Refresh Token 암호화 저장 + JWT 발급 | 📝 Plan 확정 — Feature 0 게이트 종료(2026-06-10, **하이브리드** 자격증명: admin-key=API Token/site URL, 콘텐츠=OAuth/gateway), 착수 전 결정 6건 전부 확정(#6 보관=별도 테이블 `admin_atlassian_credential`, 2026-06-11). 전 Feature 코드 착수 가능 |

---

# 3단계 — Authorization Server

> 착수 전 **§착수 전 결정 필요** 의 항목을 먼저 확정한다(1~6 전부 확정 — #6 admin API Token 보관 = 별도 테이블 `admin_atlassian_credential`, 2026-06-11). 참조: `backend/rules/auth.md`, `backend/auth-server/CLAUDE.md`, `docs/api-spec.md` §4-1·§2-5, `docs/db-schema.md` §6, `docs/adr/0001-page-level-acl-source.md`.

## 작업 요약 (credential payload 미포함, 2026-06-05 갱신)

3단계는 admin OAuth token 을 MySQL 에 암호화 저장하되, Data Ingestion HTTP/RabbitMQ payload 에 Confluence credential set 을 싣지 않는 방식으로 정정한다. 기존 PoC 직접 전달 결정은 2026-06-05 RabbitMQ completion event 결정으로 대체한다.

| 모드 | 흐름 | 적용 시점 |
|---|---|---|
| **현재** | BFF/Auth → Atlassian OAuth callback → access_token 발급 → `accessible-resources` 호출로 cloudId 조회 → **사용자 단위로 MySQL 에 암호화 저장**(access/refresh token + cloudId) → Data Ingestion Worker 가 `adminUserId` 로 내부 credential 조회 API 호출 → `accessToken + cloudId` 를 함께 조회해 Confluence 호출에 사용 | **3단계 (현재)** |
| 후속 확장 | 위와 동일 저장. 필요 시 credential 조회 응답을 `connectionId` 등 단기 참조 토큰으로 한 번 더 감싸 Data Ingestion Worker 의 token 보유 시간을 줄임 | **3단계 이후 별도 라운드** (cutover 시점 사전 합의) |

## 외부 OAuth 계약 참조 (Atlassian 3LO)

> 출처: 팀 사전 정리 노트(AUTH-01~04) + Atlassian 공식 문서. **우리 서비스 API 가 아니라 Atlassian 제공자 엔드포인트**이며, 아래 Feature(authorize 리다이렉트·code 교환·refresh·cloudId 조회)가 호출하는 외부 계약이다. `docs/api-spec.md` §4-1 의 FE-facing 엔드포인트와 혼동 금지. 값은 초안이므로 구현 전 공식 문서로 재검증한다.
> 공식 문서: https://developer.atlassian.com/cloud/confluence/oauth-2-3lo-apps/

| #       | 엔드포인트                                                        | 용도                          | 우리 쪽 트리거                          |
| ------- | ----------------------------------------------------------------- | ----------------------------- | --------------------------------------- |
| AUTH-01 | `GET https://auth.atlassian.com/authorize`                        | 인가 화면 리다이렉트          | `GET /api/auth/login` 의 `Location`     |
| AUTH-02 | `POST https://auth.atlassian.com/oauth/token` (`authorization_code`) | code → access/refresh 교환 | `GET /api/auth/callback` 내부           |
| AUTH-03 | `POST https://auth.atlassian.com/oauth/token` (`refresh_token`)   | access token 갱신             | `POST /api/auth/refresh` 내부 (Confluence 토큰 갱신) |
| AUTH-04 | `GET https://api.atlassian.com/oauth/token/accessible-resources`  | 접근 가능 사이트·cloudId 조회 | callback 후 cloudId 확보                |
| AUTH-05 | `GET https://api.atlassian.com/ex/confluence/{cloudId}/wiki/rest/api/user/memberof?accountId={accountId}` | 사용자 group 멤버십(`name`+`id`) 조회 | callback 에서 JWT `groups` claim 적재 |

- **authorize(AUTH-01) 쿼리**: `audience=api.atlassian.com`, `client_id`, `scope`, `redirect_uri`, `state`(CSRF), `response_type=code`, `prompt=consent`. 콜백으로 `code`+`state` 수신.
- **토큰 교환(AUTH-02)**: body `grant_type=authorization_code` / `client_id` / `client_secret` / `code` / `redirect_uri` → 응답 `access_token` / `expires_in` / `scope`.
- **토큰 갱신(AUTH-03)**: body `grant_type=refresh_token` / `client_id` / `client_secret` / `refresh_token` → 응답에 **새 `access_token` + 새 `refresh_token`** + `expires_in` / `scope`. 실패 시 `{"error":"invalid_grant"}`.
- **cloudId(AUTH-04)**: header `Authorization: Bearer {access_token}` → 배열, 각 `{ id(=cloudId), name, url, scopes, avatarUrl }`. `id`=cloudId 는 callback 에서 `user_tokens` 에 저장. `url`(=브라우저 base URL `https://{site}.atlassian.net`)은 admin-key 관리용으로 `admin_atlassian_credential.site_url`(§6.4)에 보관되며, §2-5 에서 `siteUrl`(JSON) 로 ingestion 에 반환된다(별도 저장 없음). 이후 Confluence REST URL: `https://api.atlassian.com/ex/confluence/{cloudId}/rest/api/...`. **공식 주의: 반환 사이트는 순서가 없고 "가장 최근 인가" 식별 용도로 쓰면 안 됨** → 멀티 사이트 시 선택 규칙 필요(PoC 는 단일 사이트 가정, 다중이면 설정값/명시 선택).
- **groups(AUTH-05)**: `GET /ex/confluence/{cloudId}/wiki/rest/api/user/memberof?accountId={accountId}` → `results[].{ name, id(groupId) }`. **페이지네이션**(`start`/`limit`(기본 200)/`size`/`totalSize`) — `totalSize > limit` 이면 페이징 처리. 권한: Confluence 사이트 'Can use' global permission. **groups 는 토큰 디코딩이 아니라 본 API 호출로 취득**하며 callback 에서 JWT `groups` claim 에 적재한다. **claim 값 = `id`(groupId)** — `name` 아님(2026-06-09 확정, RAG `allowed_groups` 정합).

### 구현 시 주의 (노트 기반 — 검증 필요)

- **`offline_access` scope 필수** — refresh_token 발급 조건. authorize 의 `scope` 에 포함한다.
- **Rotating Refresh Token** — 갱신 시 기존 refresh 가 무효화되고 새 값이 발급된다 → **MySQL 암호화 저장소에 반드시 덮어쓰기**하고 이전 값은 재사용 금지.
- **scope 충분성 (✅ 확정 2026-06-10 → ⚠️ 2026-06-12 classic 통일로 정정)** — 요약 scope 는 본문 미포함이라 부족. **최종 scope(7종): `read:me`(user-info `/me` — Confluence 가 아닌 **User Identity API** 의 scope, 누락 시 `/me` 403 — 라이브 스모크 실측 2026-06-12), `read:confluence-content.all`(본문 + v1 restriction 조회 — `GET /content/{id}/restriction` operation 문서의 공식 classic scope), `readonly:content.attachment:confluence`(첨부 다운로드 — 형태는 granular 같지만 **Classic 표 등재**, 형태 규칙의 예외), `read:confluence-user`(AUTH-05 memberof — operation 문서상 이것 하나로 충분), `read:confluence-groups`, `read:confluence-space.summary`, `offline_access`.** dev console 은 Confluence API 외에 **User Identity API**(`read:me`)도 등록 필요(타 product scope 혼합은 공식 지원 — classic/granular 충돌과 무관). 06-10 셋에서 제거는 **`read:content.restriction:confluence` 단 1개**(유일한 granular — dev console 미등록 granular 를 authorize 에 요청해 거부된 원인. restriction 조회는 위 `content.all` 로 커버되어 기능 손실 없음). **전제: 콘텐츠 조회는 v1 API**(classic↔v1) — 수집측이 v2(`/wiki/api/v2/*`) 사용 시 granular 별도 등록 필요. 최종 검증 방법론(공식): 수집측 실제 호출 operation 목록 확정 후 REST 문서의 operation 별 scope 행으로 대조.
- `client_secret` 등은 평문 노출 금지(환경변수/Secret), 토큰 로그 마스킹(Feature 7).

---

## 용어 구분 (혼동 방지)

| 토큰 | 발급자 | 저장 위치 | 용도 |
|---|---|---|---|
| **Confluence OAuth access/refresh** | Atlassian | MySQL `user_tokens` (암호화) | Confluence REST 호출. **FE 미노출** |
| **LINA 세션 access JWT** | auth-server | FE 보관 | `Authorization: Bearer` 로 BFF 호출 |
| **LINA refresh token** | auth-server | FE 보관 + 서버 검증용 저장 | 세션 JWT 갱신 (`POST /api/auth/refresh`) |

> AUTH-03(Confluence refresh)과 `POST /api/auth/refresh`(LINA 세션 refresh)는 **다른 토큰**이다. 전자는 Confluence 토큰 만료 시 내부적으로(주로 Feature 5 credential 조회 시) 갱신하고, 후자는 FE 세션 갱신 계약이다.

---

## 변경 대상 파일 (개요)

### 신규 — 코드 (`auth-server/src/main/java/com/lina/auth/` 하위)
- `oauth/` — `AuthController`(`/api/auth/*`), `AtlassianOAuthClient`(AUTH-02/03/04 호출), `OAuthStateService`(state+mode 직렬화/검증), `dto/`(토큰 응답·콜백 등)
- `jwt/` — `JwtProvider`(발급/검증), `JwtProperties`(서명키·TTL·issuer), `JwtClaims`
- `token/` — `User`/`UserToken`/`UserGroup`/`AdminAtlassianCredential` Entity, `*Repository`, `TokenCipher`(AttributeConverter 암호화), `SessionService`(refresh 회전/무효화) — `groups` 는 **`user_groups` 테이블 영속**(로그인 시 AUTH-05 `memberof` 적재). 스페이스 단위 `user_space_acl` 미사용. `AdminAtlassianCredential`=admin-key 관리용 `site_url`+`admin_api_token_enc`(§6.4)
- `internal/` — `InternalCredentialController`(`/internal/auth/admin-confluence-credential`, OAuth accessToken+cloudId+siteUrl 반환), `AdminKeyController`(`/internal/admin/key/{activate,deactivate}`), `AdminKeyClient`(Atlassian admin-key — `admin_atlassian_credential` 의 site URL+API Token Basic auth)
- (`/api/users/me` 는 auth-server 가 아니라 **BFF** 가 MySQL `users` 를 읽어 서빙 — api-spec §3 흐름도 `users/me → BFF → DB`. §교차 모듈 참조)
- `config/` — `SecurityConfig`(Bearer 검증 + 내부 API 호출자 제한), `RestClientConfig`(Atlassian RestClient)
- `support/` — 공통 헬퍼(필요 시)

### 신규 — DB
- `auth-server/src/main/resources/db/migration/` — Flyway `V001__create_users.sql`, `V002__create_user_groups.sql`, `V003__create_user_tokens.sql` **(작성 완료 2026-06-10)**, `V004__create_admin_atlassian_credential.sql` **(작성 완료 2026-06-11 — admin-key 관리용 `site_url`+`admin_api_token_enc` 테이블)**. admin seed 는 후속(별도 마이그레이션)
- `docs/db-schema.md` §6.1~§6.4 (`users`/`user_tokens`/`user_groups`/`admin_atlassian_credential`) — **작성 완료** (§6.1~§6.3 2026-06-10, §6.4 + `users.refresh_token` 선반영 2026-06-11)

### 신규 — 테스트 (`auth-server/src/test/java/com/lina/auth/` 하위)
- `token/*RepositoryTest`(@DataJpaTest), `jwt/JwtProviderTest`, `oauth/AtlassianOAuthClientTest`(WireMock), `oauth/AuthControllerTest`(MockMvc), `internal/*ControllerTest`, `user/UserControllerTest`

### 변경 — 설정/빌드
- `auth-server/build.gradle` — `spring-boot-starter-data-jpa`, `com.mysql:mysql-connector-j`, `org.flywaydb:flyway-core`/`flyway-mysql`, 테스트 H2 또는 Testcontainers(MySQL), WireMock 추가
- `auth-server/src/main/resources/application*.yml` — datasource(`${...}`), JPA, Flyway, 토큰 암호화 키(`${...}`), JWT 키/issuer/TTL(`${...}`), Atlassian OAuth client-id/secret/redirect-uri(`${...}`)

## 수정하지 않을 파일
- `CLAUDE.md`, `backend/CLAUDE.md`, `backend/rules/*`, `backend/auth-server/CLAUDE.md`
- `docs/architecture.md`, `docs/conventions.md`, `scripts/*.sh`, root `build.gradle`/`settings.gradle`/`gradle*`
- `common` 모듈 (응답/예외 포맷 그대로 사용)
- `bff-server` 모듈 — **3단계 BFF 측 작업(gateway 라우팅·JWT 검증 필터 교체·`CurrentUserProvider` JWT 구현체)은 `bff-server/current-plans.md` 에 별도 Feature 로 추적**(본 plan 범위 밖, §교차 모듈 참조)

---

## 구현 Feature 및 체크리스트 (PoC)

> `docs/ai/workflow.md` §3 (AC → 실패 테스트 → 최소 구현 → 검증) 순서. Feature 는 번호 순서대로 진행하며 별도 세션/커밋으로 분리 가능.

### Feature 0. 자격증명 모델 검증 게이트 (스파이크 — 코드 산출물 최소)

#### 변경 대상 파일
- 없음 (검증 노트만 본 plan §확정된 결정 / working-log 에 기록)

#### 체크리스트
- [x] 모델 판정(`200`→OAuth 유지 / `401/403/404`→API Token 전환) — ✅ **admin API Token 모델 전환 확정(2026-06-10)**, §검증 노트
- [x] AUTH-01 authorize 의 `scope` 충분성 확인 — ✅ 확정(2026-06-10), §구현 시 주의 'scope 충분성'
- [x] 결과를 §확정된 결정 표에 1행으로 기록(모델·scope) — ✅ 'admin ingestion 자격증명 모델' 행

> ✅ **게이트 종료(2026-06-10·하이브리드 정정 06-11)** — admin-key 관리 = admin API Token(Basic)/site URL, 콘텐츠 조회 = OAuth Bearer/gateway + Admin Key 헤더. (보관 #6: admin API Token=별도 테이블 `admin_atlassian_credential`, OAuth=`user_tokens`. 확정 2026-06-11.)

#### 검증 노트 (2026-06-10)

- **OAuth2 차단(공식 문서)**: v2 `/admin-key` 는 OAuth2/Forge/Connect 앱 접근 불가 → "저장 OAuth token 으로 admin-key 호출"(기존 Feature 6 전제) 성립 불가. 헤더 자체는 동작(Atlassian 팀 공식 답변 — 커뮤니티의 "미동작" 보고는 일반 사용자 댓글). TTL 기본 10분·`durationInMinutes` 최대 60분, Premium/Enterprise 전용.
- **라이브 실측(2026-06-02 팀 테스트, 'Confluence Admin Key API 테스트 결과 정리')**: 우리 Premium 사이트에서 API Token Basic auth 로 activate(`POST {site}/wiki/api/v2/admin-key`) `200` → 헤더 포함 시 restriction 페이지 **404→200**(목록 232→237개) → deactivate(`DELETE`) `204`. 권한 없는 페이지는 403 아닌 404.
- **하이브리드 모델 정정(2026-06-11)**: 게이트 결론(admin-key=API Token)은 **admin-key 관리 호출에만** 적용된다. 두 호출로 분리: **① admin-key 관리** = admin API Token + Basic + **site URL**(`{siteUrl}/wiki/api/v2/admin-key`), siteUrl·token 은 `admin_atlassian_credential` 테이블(§6.4) 보관. **② 콘텐츠 조회**(pages/spaces/restrictions) = admin **OAuth Bearer** + **게이트웨이**(`api.atlassian.com/ex/confluence/{cloudId}/...`) + Admin Key 헤더, OAuth·cloudId 는 `user_tokens` 보관. (앞서 콘텐츠 조회까지 API Token/Basic 으로 적은 것은 과잉정정 — 환원.) 두 base URL 은 통일하지 않는다.
- **수집측 핸드오프(완료)**: page-level restriction 이 비어도 상위 folder/space 계층으로 제한될 수 있음(실측 5개 중 2개) → page restriction 만으로 ACL 을 만들면 over-permissive(유출 방향). effective 권한 산출은 ingestion 측에서 해결 예정(2026-06-10 확인, ADR 0001 영역) — auth-server 추가 작업 없음.

> **왜 먼저인가:** 이 결과가 Feature 1(저장 스키마)·Feature 5(credential 조회 응답) 설계를 바꾼다. 코드 착수 전 게이트.

---

### Feature 1. MySQL 영속 계층 + 스키마 문서

#### 변경 대상 파일
- `build.gradle`, `application*.yml`, `docs/db-schema.md` §6.2·§6.4
- `db/migration/V001__create_users.sql`, `V002__create_user_groups.sql`, `V003__create_user_tokens.sql`, `V004__create_admin_atlassian_credential.sql` **(작성 완료)**
- `token/entity/{User,UserGroup,UserToken,AdminAtlassianCredential}.java`, `token/TokenCipher.java`(AttributeConverter), `token/repository/*Repository.java`
- `token/repository/*RepositoryTest.java`

#### 체크리스트
- [x] `docs/db-schema.md` §6.1~§6.4 **작성 완료**: `users`(PK `user_key` UUID, `user_id`=accountId UNIQUE, `email` UNIQUE, `name`/`profile_image_url`/`role`/LINA `access_token`/**`refresh_token` 선반영**/`last_login_at`)·`user_groups`(`group_id`=groupId 영속, 복합 PK)·`user_tokens`(`user_key` FK, `confluence_access/refresh_token_enc` AES-GCM, `cloud_id`, `access_token_expires_at`)·**`admin_atlassian_credential`**(admin-key 관리용 `site_url`+`admin_api_token_enc`, §6.4). 스페이스 단위 `user_space_acl` 미사용 — 페이지 ACL 은 수집단계 Qdrant payload(ADR 0001 §2). (§6.1~§6.3 2026-06-10, §6.4+`refresh_token` 2026-06-11)
- [x] `build.gradle`: `spring-boot-starter-data-jpa`, `mysql-connector-j`, `flyway-core`/`flyway-mysql`, 테스트 H2(또는 Testcontainers MySQL)·WireMock — ✅ 2026-06-11 (H2 + WireMock 선반영)
- [x] `application*.yml`: datasource/JPA/Flyway, 토큰 암호화 키, Atlassian client-id/secret/redirect-uri, JWT 키/issuer/TTL — **모두 `${...}` 환경변수, 평문 secret 금지** — ✅ 2026-06-11 (datasource `MYSQL_*`/JPA `ddl-auto: validate`/Flyway 추가, 암호화·OAuth·JWT 키는 기존 `${...}` 유지. 테스트는 `src/test/resources/application-test.yml` H2 + 테스트 전용 키)
- [x] `users`(PK `user_key` BINARY16 UUID, `user_id`=accountId UNIQUE, `email` UNIQUE, `role` ENUM, LINA `access_token`/`refresh_token`) / `user_groups`(`group_id`=groupId, 복합 PK·FK CASCADE) / `user_tokens`(`user_key` FK, `confluence_*_token_enc` 암호화, `cloud_id`) / `admin_atlassian_credential`(`user_key` FK, `site_url`, `admin_api_token_enc` 암호화) Entity + Repository — ✅ 2026-06-11 (`token/entity/*` 4종 + `UserRole`/`UserGroupId`, `token/repository/*` 4종)
- [x] **토큰 암호화**: `TokenCipher` AttributeConverter 로 access/refresh 컬럼 암호화 저장(평문 저장 금지). **AES-GCM + env 주입 키**(확정 2026-06-09, 운영 KMS/Vault) — ✅ 2026-06-11 (IV 12B‖GCM 암호문, 키 미주입 시 fail-fast, `TokenCipherTest` 6건)
- [x] Flyway `V001` users(`refresh_token` 선반영)·`V002` user_groups·`V003` user_tokens·**`V004` admin_atlassian_credential** **작성 완료**(V004 2026-06-11). **admin seed**(`role='ADMIN'`, 하드코딩)는 **후속**(별도 마이그레이션 — `users.access_token` NOT NULL 이라 placeholder 또는 nullable 검토)
- [x] `user_tokens`(Confluence 토큰) 조회/저장은 `user_key`(FK) 기준, refresh 회전 시 덮어쓰기(이전 값 미보존) — ✅ 2026-06-11 (`UserToken.rotate()` + 회전 덮어쓰기·1행 유지 테스트)
- [x] `@DataJpaTest`(H2/Testcontainers): users upsert, role lookup, 토큰 암호화 라운드트립(저장값이 평문이 아님), 만료시각 조회 — ✅ 2026-06-11 (H2, Repository 테스트 14건 — upsert 시 `user_key`/`role` 유지, native query 로 저장 컬럼 평문 아님 검증 포함)
- [x] **(Feature 0·#6 반영)** admin-key 관리 credential 보관 — 별도 테이블 `admin_atlassian_credential`(`site_url`+`admin_api_token_enc`, `V004` CREATE, AES-GCM). `AdminAtlassianCredential` Entity + Repository, `TokenCipher` 재사용. (콘텐츠 조회용 OAuth 토큰·cloudId 는 `user_tokens` 그대로 — admin API Token 과 분리.) 게이트웨이 base URL 은 cloudId 로 런타임 구성

---

### Feature 2. JWT 발급/검증 유틸

#### 변경 대상 파일
- `jwt/{JwtProvider,JwtProperties,JwtClaims}.java`, `jwt/JwtProviderTest.java`

#### 체크리스트
- [x] Claim 셋: `userId`(Confluence accountId), `groups`, `role`(`USER`/`ADMIN`), `iss`, `iat`, `exp` — **camelCase**(`backend/rules/auth.md` §2). `groups` 값은 Feature 3 callback 이 AUTH-05(`memberof`)로 조회해 전달(JwtProvider 는 입력받아 서명만). **group 식별자 = `groupId`**(`results[].id`, 2026-06-09 확정 — RAG Qdrant `allowed_groups` 와 동일 표기, `name` 아님) — ✅ 2026-06-11 (`JwtClaims` record + 와이어 claim 이름 테스트로 고정. access 는 계약 claim 셋 그대로, 빈 `groups` 허용)
- [x] 서명 알고리즘·키: **RS256**(확정 2026-06-09 — auth-server 개인키 서명, BFF 는 공개키만 검증). PEM/키 위치는 `${...}` env — ✅ 2026-06-11 (`JwtProperties` — `LINA_JWT_PRIVATE_KEY`/`PUBLIC_KEY` PEM env, 미주입 시 fail-fast)
- [x] access JWT 발급(TTL `${...}`) + LINA refresh token 발급/검증 유틸 — ✅ 2026-06-11 (refresh 도 RS256 JWT — `userId`+`tokenType=refresh` claim, 자체 만료. ✅ RS256 JWT 길이 이슈 해소(2026-06-11): `users.access_token`/`refresh_token` VARCHAR(512)→**2048** — `V001` 직접 수정(미적용 단계) + Entity·`docs/db-schema.md` §6.1 정합, 512 초과 토큰 저장 테스트 추가)
- [x] 검증 메서드(서명·만료·issuer) — BFF JWT 검증 필터와 **동일 Claim·키 계약**(`auth-server/CLAUDE.md` §3.2) — ✅ 2026-06-11 (`verifyAccessToken`/`verifyRefreshToken`, 토큰 타입 교차 사용 거부 포함)
- [x] `JwtProviderTest`: 발급→검증 라운드트립, 만료/서명위조 거부, Claim 값 보존 — ✅ 2026-06-11 (11건 — 라운드트립·camelCase 와이어 이름·TTL·빈 groups·만료/위조/issuer 거부·타입 교차 거부·키 미주입 fail-fast)
- [x] **모순 정정**: 기존 plan Feature C "본 라운드는 인터페이스만" → login/callback(Feature 3)이 실제 JWT 를 발급해야 하므로 **본 Feature 에서 실제 발급까지 구현**한다 — ✅ 2026-06-11 (실제 발급·검증 구현 완료)

---

### Feature 3. OAuth Authorization Code Flow (`/api/auth/login`·`/api/auth/callback`)

#### 변경 대상 파일
- `oauth/{AuthController,AtlassianOAuthClient,OAuthStateService}.java`, `oauth/dto/*.java`, `config/RestClientConfig.java`
- `oauth/{AtlassianOAuthClientTest(WireMock),AuthControllerTest(MockMvc)}.java`
- (구현 시 추가 2026-06-12) `oauth/{OAuthLoginService,OAuthProperties}.java`(오케스트레이션/설정 홀더 분리), `config/SecurityConfig.java`(login·callback permitAll + default deny — logout Bearer·`/internal/**` 제한은 Feature 4/7 에서 확장), `token/entity/User.java` `storeRefreshToken()` 추가(시그니처 변경 없음), `application.yml`(`api-base-uri`/`site-url`/`state-ttl-seconds` — 전부 `${...}`), `oauth/{OAuthStateServiceTest,OAuthLoginServiceTest}.java`

#### 체크리스트
- [x] `GET /api/auth/login` — Atlassian authorize 로 `302` 리다이렉트(Wrapper 미적용). `state`(CSRF) 생성·저장, **`mode`/`returnTo` 를 `state` 에 직렬화**(서명 state 또는 서버 세션). `returnTo` 는 **내부 경로만** 허용(오픈 리다이렉트 방지) (`docs/api-spec.md` §4-1) — ✅ 2026-06-12 (state 는 in-memory 1회용+TTL 보관 — 단일 인스턴스 전제, 다중 인스턴스 시 외부 저장소 교체 필요)
- [x] `GET /api/auth/callback` — `code`/`state` 검증 → AUTH-02 토큰 교환 → AUTH-04 `accessible-resources` 로 cloudId 조회 → **AUTH-05 `memberof` 로 groups(`groupId`=`results[].id`) 조회(페이징 처리)** → `users` upsert(없으면 `role='USER'`) + **`user_groups` 적재**(기존 멤버십 교체) → **Confluence** access/refresh + cloudId **암호화 저장**(`user_tokens`, Feature 1) → **LINA 세션 JWT 발급**(Feature 2, `userId`/`groups`/`role` claim) → `data: { accessToken, refreshToken, expiresAt }` (LINA refreshToken 발급/회전은 Feature 4 — `users.refresh_token` 컬럼은 `V001` 선반영) — ✅ 2026-06-12 (accountId/email/name 은 user-info `/me` 로 취득. LINA refreshToken 은 응답 계약상 필요해 **발급·`users.refresh_token` 저장까지** 본 Feature 에서 구현 — 회전/무효화는 Feature 4)
- [x] AUTH-05 groups 조회: `totalSize > limit`(기본 200) 이면 `start` 페이징으로 전량 수집. 조회 실패/빈 결과 시 정책 결정(빈 `groups` 허용 시 BFF fail-closed 로 RAG 차단되므로 — 로그인은 허용하되 질의 단계에서 차단되는 동작 명시) — ✅ 2026-06-12 (**정책 확정: 조회 실패 시 warn 로그 + 빈 `groups` 로 로그인 허용** — api-spec v2.6.0 'groups 빈 배열 허용' 정합, 질의는 user-level/공개 페이지만 매칭)
- [x] **`mode=admin` 게이트**: state 의 mode 가 `admin` 인데 `users.role != ADMIN` 이면 `403 FORBIDDEN`(message "관리자 권한이 없는 계정입니다"), 토큰 미발급 (`docs/api-spec.md` §4-1) — ✅ 2026-06-12 (게이트는 groups 조회·모든 영속보다 먼저 — 거부 시 INSERT/저장 없음)
- [x] 실패 매핑: `state` 불일치 `400 INVALID_REQUEST`, `code` 무효/Confluence 오류 `401 UNAUTHORIZED`, mode 게이트 `403 FORBIDDEN` — 모든 실패에서 토큰 미발급 — ✅ 2026-06-12 (`code`/`state` 누락도 `400`)
- [x] `accessible-resources` 멀티 사이트 선택 규칙 — **공식 주의: 반환 순서 없음, "최근 인가" 추론 금지**. PoC 는 단일 사이트 가정, 다중이면 설정값/명시 선택(첫 번째 임의 선택 금지)·기록 — ✅ 2026-06-12 (단일=자동, 다중=`CONFLUENCE_SITE_URL` 일치 사이트만 선택, 미설정/불일치 시 `500 INTERNAL_ERROR`, 0개 시 `401`)
- [x] Confluence OAuth 토큰은 **응답에 미포함**(서버 보관, `backend/rules/auth.md` §3) — ✅ 2026-06-12 (응답 미포함 테스트로 고정)
- [x] WireMock 으로 AUTH-02/04 모킹, MockMvc 로 login 리다이렉트·callback 성공/실패(400/401/403) 검증. **실제 Atlassian 호출 금지** — ✅ 2026-06-12 (WireMock: AUTH-02/04/05+`/me`+페이징, MockMvc 8건, Service 단위 13건, state 5건 — 신규 31건 전부 통과)

> **FE 진입점 정합 (api-spec §3):** FE 단일 진입점은 BFF 다. auth-server 가 `/api/auth/login`·`/api/auth/callback` 핸들러를 호스팅하고 BFF gateway 가 `/api/auth/**` 를 라우팅한다(FE 는 BFF 만 본다). 흐름도 표기는 `GET /api/auth/callback → BFF → Auth Server: code 교환·users upsert·JWT 발급` — 처리 책임은 auth-server. (**BFF gateway path-through (a) 확정 — 2026-06-09**, §확정된 결정 'FE 진입점·gateway 라우팅')

---

### Feature 4. 세션 관리 (`/api/auth/refresh`·`/api/auth/logout`)

#### 변경 대상 파일
- `oauth/AuthController.java`(엔드포인트 추가), `token/SessionService.java`, `config/SecurityConfig.java`
- `oauth/AuthControllerTest.java`(refresh/logout 추가)
- (구현 시 추가 2026-06-12) `config/JwtAuthenticationFilter.java`(Bearer 검증→SecurityContext 적재 — logout 호출자 식별), `oauth/dto/RefreshTokenRequest.java`, `token/entity/User.java` `rotateSessionTokens()`/`clearRefreshToken()` 추가(시그니처 변경 없음), `token/SessionServiceTest.java`

#### 체크리스트
- [x] `POST /api/auth/refresh` — LINA refresh token(Body) 검증 → **Rotating**: 새 access JWT + 새 refresh 발급, 이전 refresh 무효화. 만료/무효 시 `401 UNAUTHORIZED` — ✅ 2026-06-12 (재사용 거부는 JWT 검증 + **`users.refresh_token` 저장값 대조**로 구현 — stateless 검증만으론 회전 후 재사용을 못 잡음. 별도 토큰 테이블 미신설. 권한 claim 은 refresh 시 DB 재조회, 새 access JWT 는 `users.access_token` 에도 갱신)
- [x] `POST /api/auth/logout` — `Authorization: Bearer` 로 식별, refresh token 무효화, `data: null` — ✅ 2026-06-12 (무효화 = `users.refresh_token` NULL 비움 → 이후 refresh 401)
- [x] `SecurityConfig` — `/api/auth/login`·`/api/auth/callback` 은 `permitAll`, `/api/auth/logout` 은 Bearer 필요, `/internal/**` 은 외부 차단 — ✅ 2026-06-12 (`/api/auth/refresh` 도 permitAll — Body 의 refresh token 으로 자체 검증. logout Bearer = `JwtAuthenticationFilter` + 401 EntryPoint(공통 ErrorResponse). `/internal/**` 는 `denyAll` — Feature 5/7 에서 내부 호출자 인증으로 대체)
- [x] `AuthControllerTest`: refresh 회전·재사용 거부, logout 무효화 — ✅ 2026-06-12 (MockMvc 7건 추가 + `SessionServiceTest` 7건 신규: 회전·재사용 거부·logout 후 refresh 거부·미인증/위조 Bearer 401·`/internal/**` 차단. 신규 14건 red→green 확인)

> **`/api/users/me` 는 본 Feature 에서 제외** — api-spec §3 흐름도(`users/me → BFF → DB`)·`/api/admin/* → BFF → MySQL` 기준, **BFF 가 MySQL `users` 를 직접 읽어 서빙**한다. 따라서 users/me 는 BFF 작업(§교차 모듈 참조)이며, auth-server 는 `users` **쓰기**(callback upsert, Feature 3)만 담당한다. MySQL 은 3단계부터 **공유**(auth-server 가 `users`/`user_tokens` 쓰기, BFF 가 `users` 읽기).

---

### Feature 5. 내부 credential 조회 API (Data Ingestion Worker 전용)

#### 변경 대상 파일
- `internal/InternalCredentialController.java`, `token/SessionService.java`(refresh 재사용), `internal/InternalCredentialControllerTest.java`
- (구현 시 추가 2026-06-12) `internal/InternalCredentialService.java`(Confluence AUTH-03 refresh 는 LINA 세션 담당인 SessionService 재사용 대신 **별도 서비스**로 구현 — §용어 구분 표의 토큰 분리 정합), `internal/dto/AdminConfluenceCredentialResponse.java`, `config/InternalApiKeyFilter.java`(내부 호출자 인증), `config/SecurityConfig.java`(`/internal/auth/**` ROLE_INTERNAL + 403 AccessDeniedHandler), `oauth/AtlassianOAuthClient.java`(AUTH-03 `refreshAccessToken` + `InvalidGrantException`), `oauth/dto/TokenExchangeRequest.java`(`refreshToken` 필드·NON_NULL), `application*.yml`(`lina.internal.api-key=${INTERNAL_API_KEY}`), `internal/InternalCredentialServiceTest.java`, `oauth/AtlassianOAuthClientTest.java`(AUTH-03 3건)

#### 체크리스트
- [x] `GET /internal/auth/admin-confluence-credential?adminUserId={id}` — `adminUserId` 로 조회 → `users.role == ADMIN` 확인 → `user_tokens`(`accessToken`/`cloudId`) + `admin_atlassian_credential`(`site_url` 컬럼→`siteUrl` JSON) 로드 → access token 만료/임박 시 AUTH-03 refresh → DB 최신화 → `{ accessToken, cloudId, siteUrl, expiresAt }` 반환(`refreshToken` 미반환). siteUrl=`admin_atlassian_credential.site_url` 그대로(별도 저장 없음), ingestion 출처 URL 정규화용·secret 아님 — ✅ 2026-06-12 (§2-5 raw JSON 계약. 만료 임박 skew 60초, rotating 덮어쓰기·외부 호출은 트랜잭션 밖 — SessionService 와 동일 원칙)
- [x] 호출 주체 제한: Data Ingestion Worker 전용(내부 service auth/NetworkPolicy). FE/BFF/외부 차단 — ✅ 2026-06-12 (`X-Internal-Api-Key` 헤더 ↔ `${INTERNAL_API_KEY}` 상수시간 비교, 키 미설정 시 fail-closed 전부 거부. `/internal/auth/**`=ROLE_INTERNAL, 사용자 JWT 는 403, 잔여 `/internal/**` denyAll 유지(Feature 6 미개방). api-spec §2-5 헤더 계약 반영)
- [x] 에러 정책: `adminUserId` 누락 `400`, 사용자/credential 없음 `404`, `role != ADMIN` `403`, refresh `invalid_grant` `401`(+재로그인 필요 상태 기록), Atlassian 일시 장애 `502 EXTERNAL_SERVICE_ERROR` — ✅ 2026-06-12 (invalid_grant 는 `InvalidGrantException` 으로 구분, warn 로그 기록 — 토큰 원문 미포함·userId 만)
- [x] 응답 access_token 평문은 **내부 API 한정** — 로그/tracing 마스킹(Feature 7) 적용 — ✅ 2026-06-12 (응답 body 미로깅·토큰 로그 없음 확인. 전역 마스킹 규칙은 Feature 7 에서 일괄)
- [x] 테스트: role 분기(403), 만료시 refresh 후 최신 토큰 반환, `refreshToken` 미노출, WireMock 으로 AUTH-03 모킹 — ✅ 2026-06-12 (신규 24건 red→green: MockMvc 11건 — **내부 키 없는 외부 호출 401·위조 키 401·사용자 Bearer 403 전부 토큰 미반환** 포함, Service 단위 10건, AUTH-03 WireMock 3건. 전체 101건 통과)
- [x] **(하이브리드 정정 2026-06-11)** 본 §2-5 API 는 **admin OAuth `accessToken` + `cloudId`(user_tokens) + `siteUrl`(JSON, =`admin_atlassian_credential.site_url` 컬럼)** 을 반환. `accessToken`/`cloudId`=Bearer+게이트웨이 콘텐츠 조회, `siteUrl`=출처 URL absolute 정규화(별도 저장 없이 `site_url` 컬럼 그대로 전달). `refreshToken` 미반환, 만료 임박 시 AUTH-03 refresh 후 `user_tokens` 갱신해 반환(401/502 에러 유지). **admin API Token 은 본 API 가 반환하지 않음** — admin-key 관리(Feature 6)에서 auth-server 내부 사용 — ✅ 2026-06-12 (응답 DTO 에 refreshToken/adminApiToken 필드 자체 없음 — 구조적 차단)

---

### Feature 6. Admin Key 내부 API (`/internal/admin/key/{activate,deactivate}`)

> **범위 권고:** 이 Feature 는 4단계 `/api/admin/ingest`·RabbitMQ completion event 와 강결합한다. **3단계에서는 내부 API 구현 + 단위/WireMock 테스트까지**, BFF consumer ↔ deactivate end-to-end 통합 검증은 4단계 Feature 1(RabbitMQ completion event) 과 함께 수행한다. (**범위 확정 — 2026-06-09**, §확정된 결정 'Admin Key 내부 API 범위')

> **BFF 측 계약 선머지(2026-06-12 확인):** 본 API 의 호출자(BFF `AuthAdminKeyClient`·completion event consumer)는 PR #21(`feat/#17/admin-key-deactivate`)로 **이미 main 에 머지**되어 있다. 아래 요청 계약·idempotency·TTL 항목은 그 구현과 대조해 정합화한 것이다(`bff-server/current-plans.md` 4단계 Feature 1). Feature 6 구현 전까지 BFF `/api/admin/ingest` → activate 호출은 `/internal/**` denyAll 에 막혀 실패하는 것이 정상.

#### 변경 대상 파일
- `internal/{AdminKeyController,AdminKeyClient}.java`, `internal/dto/*.java`(activate/deactivate 요청), `internal/AdminKeyControllerTest.java`
- `application*.yml`(`durationInMinutes` 설정 외부화)

#### 체크리스트
- [ ] **요청 계약(BFF 머지 구현 정합)**: activate/deactivate 모두 `POST` + JSON body **`{ adminUserId, jobId }`** — BFF `AdminKeyActivateRequest`/`AdminKeyDeactivateRequest` 와 동일. `adminUserId`(=accountId)로 `users`(`role==ADMIN` 검증, `email`=Basic auth ID)·`admin_atlassian_credential`(site URL+API Token) 조회. 응답 body 는 BFF 가 무시(`toBodilessEntity`)하므로 `expirationTime` 반환은 유지하되 계약 비의존
- [ ] `POST /internal/admin/key/activate` — `admin_atlassian_credential` 의 `site_url`+admin API Token 으로(§6.4) Atlassian **`POST {siteUrl}/wiki/api/v2/admin-key`**(`Authorization: Basic base64(adminEmail:adminApiToken)`) 호출, `expirationTime` 응답. 실측 `200`(Feature 0 §검증 노트). **반복 호출 안전** — BFF 는 미활성 확인 없이 ingest 마다 무조건 activate 호출(머지된 `AdminIngestService`). (`docs/adr/0001-page-level-acl-source.md` §2.1)
- [ ] **`durationInMinutes` = 60분(최대값) 요청, 설정(`${...}`) 외부화** — BFF 운영 문서가 "Admin Key **60분 TTL** = consumer/DLQ 복구 실패 시 최종 fallback" 으로 60분을 전제(bff plan 4단계 Feature 1·수동 activate `activatedUntil` 60분). Atlassian 자체 기본값 10분으로 두면 10분 초과 수집 job 중간에 키 만료 → 제한 페이지가 403 아닌 **404 로 조용히 누락**(Feature 0 실측). (확정 2026-06-12, §확정된 결정)
- [ ] `POST /internal/admin/key/deactivate` — Atlassian **`DELETE {siteUrl}/wiki/api/v2/admin-key`**(Basic auth, site URL) 호출, 실측 `204`. **`jobId` 기준 idempotent**(중복 completion event 안전) — BFF 계약상 중복 `jobId` 는 **두 번째 Atlassian DELETE 없이 2xx 성공** 응답(4xx 금지 — BFF 의 DLQ 이동 조건에 걸림). TTL 은 fallback
- [ ] idempotency 저장: 처리된 `jobId` 는 **in-memory TTL store**(단일 인스턴스 전제 — Feature 3 `OAuthStateService` 의 in-memory state 와 동일 전제, 다중 인스턴스 시 외부 저장소 교체). Admin Key TTL(60분)이 fallback 이므로 entry TTL 도 그 이상이면 충분, 별도 테이블 미신설
- [ ] 에러 정책: body 필수 필드(`adminUserId`/`jobId`) 누락 `400`, 사용자/credential 없음 `404`, `role != ADMIN` `403`, Atlassian 일시 장애 `502 EXTERNAL_SERVICE_ERROR`. 단 **중복 `jobId` deactivate 는 에러가 아닌 2xx**(위 idempotency)
- [ ] 호출 주체 제한(내부 API). BFF completion event consumer / `/api/admin/ingest` 묶음 처리만 호출
- [ ] 테스트: WireMock 으로 Atlassian admin-key API 모킹, activate `durationInMinutes=60` 전송·만료시각 검증, activate 반복 호출 안전, deactivate idempotency(중복 jobId → Atlassian DELETE 1회·2xx), 에러 매핑(400/403/404/502)
- [ ] (4단계 연계) BFF consumer → deactivate 통합 검증 항목은 4단계 plan 에 추적

---

### Feature 7. 보안 운영 규칙 (전 라운드 적용)

#### 변경 대상 파일
- `config/SecurityConfig.java`, `application*.yml`, 로깅/마스킹 유틸(필요 시), `config/SensitiveConfigurationTest.java`

#### 체크리스트
- [ ] 토큰 로그/tracing 본문 마스킹 (Confluence access/refresh, LINA refresh 모두)
- [ ] actuator `env`/`heapdump`/`threaddump` 비노출
- [ ] `/internal/**` 호출자 화이트리스트(NetworkPolicy 또는 내부 service auth) — `docs/api-spec.md` §2-2 정합
- [ ] RabbitMQ/HTTP payload 에 `accessToken`/`refreshToken`/`cloudId` 미포함(본 모듈이 발행하는 경우)
- [ ] 평문 secret 미포함(OAuth client-secret·DB·암호화 키·JWT 키 모두 `${...}`) — `SensitiveConfigurationTest` 로 고정

---

### Feature 8. 검증

#### 체크리스트
- [ ] `./scripts/format.sh`/`lint.sh`/`test.sh`/`verify.sh` 성공
- [x] `./gradlew :auth-server:bootRun` 기동 확인 (MySQL 연결, Flyway 마이그레이션 적용) — ✅ 2026-06-12 (Feature 4 직후 선행 수행 — 실 MySQL 8.4 도커에서 V001~V004 적용 + `ddl-auto: validate` 통과, drift 없음. 위험 요소 'Flyway 미검증' 해소)
- [ ] OAuth login→callback→refresh→logout→users/me 라이브 스모크(Atlassian 은 mock/WireMock 또는 실제 3LO 1회) — 정상/실패(400/401/403) 경로 — **⏳ users/me 제외 전부 완료(2026-06-12, 실제 3LO)**: login 302→동의→callback 200(JWT claim: accountId/groupId 3건/USER), refresh 회전 200·**이전 refresh 재사용 401**, logout 200·**logout 후 refresh 401**, Bearer 누락 401, 만료 code 401·위조 state 400, DB 암호화 저장(평문 아님)·`user_groups` 적재 실측 확인. 스모크가 잡은 결함 2건(`read:me` scope 누락 → §구현 시 주의 정정, 토큰 컬럼 2048 truncation → V003 8192 확장) 수정·회귀 테스트 완료. **잔여: users/me(BFF 측 작업 — §교차 모듈 참조) 후 체크**
- [ ] `docs/api-spec.md` §4-1 / §2-5 명세와 구현 정합성 확인 (불일치 시에만 수정)
- [ ] `git diff` 기준 의도하지 않은 변경 / 담당 외 파일(bff-server 등) 미수정
- [ ] `backend/auth-server/CLAUDE.md` §5 + `backend/CLAUDE.md` §7 점검: OAuth 토큰 평문 저장 없음(암호화), Confluence 토큰 FE 미노출, JWT Claim 셋 BFF 일치, 평문 secret 없음, 인증 우회 코드 없음(Test Security Config 사용)

---

## 교차 모듈 참조 (BFF 측 작업 — 본 plan 범위 밖)

3단계 완료 시 BFF 가 데모 인증을 실제 JWT 검증으로 교체해야 한다. 아래는 `bff-server/current-plans.md` 에 별도 Feature 로 추적한다.
- [ ] BFF gateway 가 `/api/auth/**` 를 auth-server 로 라우팅 (FE 단일 진입점 — api-spec §3)
- [ ] **`GET /api/users/me` 를 BFF 가 직접 서빙** — BFF 에 MySQL `users` **read** datasource 추가, Bearer 검증 후 `userId`/`name`/`email`/`role`/`profileImageUrl`/`lastLoginAt`(KST) 반환, 미인증 `401`. (api-spec §3 `users/me → BFF → DB`. 관리자 대시보드 `/api/admin/*`(4단계)도 BFF→MySQL 읽기라 datasource 공유)
- [ ] `DemoSecurityConfig`(permitAll) 제거 → JWT 검증 `SecurityFilterChain` 으로 교체
- [ ] `CurrentUserProvider` 를 JWT Claim 기반 구현체로 교체(`FixedDemoUserProvider` 대체) — Controller/Service 재작성 불필요
- [ ] `lina.demo.*` 데모 설정 제거

---

## 위험 요소

| 리스크 | 영향 | 완화책 |
|---|---|---|
| ~~OAuth scope 가 RAG 본문/첨부 수집에 부족(요약 scope)~~ | 수집 단계 실패 | ✅ **해소(2026-06-10)** — Feature 0 에서 공식 문서로 scope 확정(§확정된 결정) |
| ~~단일 OAuth + Admin Key 헤더 모델이 실제로 restriction 우회 못 함~~ | 자격증명 모델 재설계 | ✅ **현실화·해소(2026-06-10)** — OAuth2 앱 차단 공식 확인, API Token 모델로 전환 확정(Feature 1·5·6 보강 반영) |
| 토큰 평문 저장/로그 노출 | 보안 사고 | AttributeConverter 암호화 + 로그 마스킹(Feature 7), `SensitiveConfigurationTest` |
| Rotating refresh 미덮어쓰기로 이전 토큰 재사용 | 인증 무효화 실패 | refresh 회전 시 저장소 덮어쓰기, 재사용 거부 테스트(Feature 1·4) |
| `groups` 조회 실패/페이징 누락 | JWT groups 불완전 → 질의 ACL 과소·과대 | groups 출처 확정(AUTH-05 `memberof`, 2026-06-09). `totalSize` 전량 페이징, 조회 실패 시 정책(Feature 3) |
| MySQL 운영 인스턴스 미가동 부팅 | 컨텍스트 실패 | Flyway/JPA 기동 전제 — 로컬은 docker MySQL, 헬스/레디니스 분리 |
| **Flyway V001~V004 미검증**(2026-06-11) — Repository 테스트는 H2 + Entity 기반 create-drop 이라 마이그레이션 SQL 을 실행하지 않음 | 컬럼명/타입 entity↔migration drift 잠복 | `ddl-auto: validate` 로 bootRun(Feature 8, 실 MySQL) 부팅 시 Entity↔실 스키마 대조 — Feature 8 이 마이그레이션 최초 검증 게이트. Testcontainers MySQL 은 ALTER/데이터 이관 마이그레이션이 생기는 시점에 도입 검토 |
| Admin Key 내부 API 의 4단계 강결합 | 3단계 단독 검증 곤란 | Feature 6 은 인터페이스+WireMock 까지, 통합은 4단계와 함께(범위 권고) |

## 문서 수정 필요 여부

- `docs/db-schema.md` — §6.1~§6.4(`users`/`user_groups`/`user_tokens`/`admin_atlassian_credential`) **작성 완료**(§6.4+`users.refresh_token` 2026-06-11). 향후 컬럼 변경 시 SQL 마이그레이션과 함께 갱신
- `docs/api-spec.md` — §4-1/§2-5 와 구현 정합성 확인(현재 명세와 일치 예상 → 변경 최소, 불일치 시에만)
- `docs/architecture.md`, `docs/conventions.md`, `docs/ai/*` — 불필요(정의 구조 준수)

## 3단계 완료 기준 (Done Definition)

- [ ] `/api/auth/login`·`/callback`·`/refresh`·`/logout`·`/api/users/me` 가 `docs/api-spec.md` §4-1 대로 동작(성공·400/401/403 실패 경로 포함)
- [ ] Confluence access/refresh 토큰이 MySQL 에 **암호화 저장**(평문 없음), FE 응답 미노출
- [ ] JWT Claim(`userId`/`groups`/`role`/`iss`/`iat`/`exp`)이 BFF 검증 계약과 일치, 서명·만료 검증 동작
- [ ] `users` upsert + `role` DB 단일 source + 최초 admin seed(Flyway) 동작
- [ ] `/internal/auth/admin-confluence-credential` 가 role 검증·만료 refresh·credential 반환(refreshToken 미노출)
- [ ] `docs/db-schema.md` §6.1~§6.4 작성 완료, Entity 와 일치
- [ ] `Repository @DataJpaTest` / `JwtProviderTest` / WireMock(AUTH-02/03/04·admin-key) / MockMvc 테스트 통과
- [ ] 평문 secret 미포함, 인증 우회 코드 없음, 토큰 로그 마스킹
- [ ] `format`/`lint`/`test`/`verify` 통과, `git diff` 담당 외 파일 미수정

---

## 착수 전 결정 필요 (open — 사용자 확인)

> 아래 항목은 Feature 구조·파일·스키마에 영향을 주므로 **코드 착수 전** 확정한다. 확정 시 §확정된 결정 표로 이동. (1~5 확정 2026-06-09, #6 확정 2026-06-11)

1. ~~`/api/auth/**` gateway 경로 세부~~ — ✅ **확정 = (a)** (2026-06-09): `/api/auth/**` 는 **BFF gateway path-through** 로 auth-server `/api/auth/*` 핸들러에 전달한다(FE 는 BFF 만 본다). OAuth 핸들러는 auth-server 가 호스팅(oauth2-client 보유). `/api/users/me`·`/api/admin/*` 는 BFF 가 MySQL 직접 읽기. auth-server 추가 노출면=`/internal/*`. 아래 §확정된 결정 'FE 진입점·gateway 라우팅' 참조.
2. ~~`groups` claim 적재 메커니즘~~ — ✅ **해소(2026-06-09, Atlassian 공식 문서 확인)**: callback 에서 **AUTH-05 `memberof` API**(`/wiki/rest/api/user/memberof?accountId=`) 로 groups(`name`+`id`) 조회 → JWT claim 적재. `user_space_acl` RDB 테이블 **미사용**(페이지 ACL 은 수집단계 Qdrant payload). group 식별자 = **`groupId`** 확정(2026-06-09, RAG Qdrant `allowed_groups` 와 동일). 아래 §확정된 결정 'groups/cloudId 취득 경로' 참조.
3. ~~토큰 암호화 방식~~ — ✅ **확정(2026-06-09)**: AES-GCM + env 주입 키(PoC). 운영 단계 KMS/Vault.
4. ~~JWT 서명 알고리즘~~ — ✅ **확정(2026-06-09)**: RS256(auth-server 개인키 서명, BFF 공개키 검증 — BFF 비밀키 미보유). 키는 `${...}` env(PEM).
5. ~~Admin Key 내부 API(Feature 6) 범위~~ — ✅ **확정(2026-06-09)**: 3단계는 내부 API + 단위/WireMock 테스트까지. BFF consumer↔deactivate end-to-end 통합은 4단계(RabbitMQ completion event)와 함께.
6. ~~admin API Token 보관 위치~~ — ✅ **확정(2026-06-11) = 별도 테이블 `admin_atlassian_credential`**(`site_url` + `admin_api_token_enc`, AES-GCM, `V004` CREATE). admin-key 관리 전용 자격증명이라 콘텐츠 조회용 OAuth(`user_tokens`)와 분리. `TokenCipher` 재사용, adminEmail=`users.email`. (`docs/db-schema.md` §6.4) — ingestion **콘텐츠 조회**는 admin OAuth(`user_tokens`)를 §2-5 내부 API 로 받아 사용(API Token 아님).

> ✅ 착수 전 결정 6건 전부 확정(1~5: 2026-06-09, #6: 2026-06-11). 3단계 전 Feature 코드 착수 가능. (credential push/pull 은 현행 pull 유지 — §위 회의 반영 노트)

---

## 향후 확장 방향 (회의 2026-06-09 — 3단계 범위 밖, plan-ahead)

> PoC(3단계)에서는 구현하지 않고 인터페이스/스키마가 후속 도입을 막지 않도록만 설계한다.

- **관리자 등록 승인 시스템** — 현재 최초 admin 은 Flyway `V001` 하드코딩 seed. 향후 "관리자 회원가입 요청 → 개발팀 승인 → `users.role` 승격" 워크플로우로 발전. 추가 admin 부여/박탈은 그때까지 DB UPDATE/DELETE 로 처리(현행). `users.role` 단일 source 구조는 이 확장과 호환.
- **자동 데이터 싱크** — 3단계/초기에는 **수동 동기화**(admin 이 '데이터 인제스천 파이프라인' 버튼으로 직접 실행)만 제공. 자동 싱크(예: 매일 새벽 3시 cron, 사용자 약관 동의 시 옵트인)는 **비용·구현 복잡성**으로 후순위 — 4단계 이후 별도 라운드. ※ 데이터 수집/싱크 자체는 4단계(`bff-server` 데이터 수집 트리거)·Data Ingestion 영역이며 auth-server 는 credential 조회(§Feature 5)·Admin Key activate/deactivate(§Feature 6) 만 담당.

## 확정된 결정

| 항목 | 결정 | 일자 |
|---|---|---|
| OAuth 도입 단계 | PoC 초기 문서의 accessToken/cloudId 직접 전달 방식은 2026-06-05 결정으로 폐기. Data Ingestion Worker 가 `adminUserId` 로 auth-server 내부 credential 조회 API 에서 `accessToken` + `cloudId` 를 함께 조회하고, RabbitMQ payload 에 credential set 을 넣지 않는다 | 2026-05-21 (06-05 갱신) |
| JWT 서명 알고리즘 | **RS256** — auth-server 개인키 서명, BFF 는 공개키만으로 검증(비밀키 미보유). 키(PEM)는 `${...}` env | 2026-06-09 |
| Confluence 토큰 저장 정책 | **PoC 부터 MySQL 암호화 저장**(access/refresh + cloudId, 사용자 단위) — admin-only ingest 가 세션 무관하게 동작하기 위해 callback 시 즉시 영속. 암호화 = **AES-GCM + env 주입 키**(운영 KMS/Vault) | 2026-06-02 (06-09 알고리즘 확정) |
| Admin Key 내부 API 범위 | 3단계 = `/internal/admin/key/{activate,deactivate}` 내부 API + 단위/WireMock 테스트. BFF consumer↔deactivate **end-to-end 통합은 4단계**(RabbitMQ completion event)와 함께 | 2026-06-09 |
| admin Confluence access 패턴 | ⚠️ **자격증명 부분은 아래 'admin ingestion 자격증명 모델' 행(2026-06-10)으로 대체** — UI 동선·activate/deactivate 흐름만 유지: ~~**OAuth Bearer + `Atl-Confluence-With-Admin-Key: true` 헤더**(Admin Key 활성화 후) — admin 도 일반 사용자와 동일하게 Confluence OAuth 3LO 로 로그인하며, ingestion 도 같은 OAuth 토큰 사용(별도 API Token 미보관).~~ **UI 동선** (2026-06-02 회의 확정·06-05 갱신): admin 이 "데이터 인제스천 파이프라인" 버튼 1회 클릭 → BFF `POST /api/admin/ingest` 가 key 미활성 시 auth-server `POST /internal/admin/key/activate` 자동 호출 → RabbitMQ ingest job 발행/`/ml/ingest` 위임 → Data Ingestion Worker 가 내부 credential 조회 → Confluence 호출 → completion event → BFF consumer 가 `POST /internal/admin/key/deactivate` 호출(BE 책임). `POST /api/admin/key/activate` 외부 endpoint 는 수동/테스트용. **3단계 구현 시 검증 게이트 = Feature 0**. (`docs/adr/0001-page-level-acl-source.md` §2.1) | 2026-06-02 (06-05 갱신) |
| admin ingestion 자격증명 모델·scope (Feature 0 게이트, **하이브리드**) | **두 호출로 분리**: ① **admin-key 관리**(activate/deactivate) = **admin API Token + Basic auth + site URL**(`{siteUrl}/wiki/api/v2/admin-key`) — admin-key REST 는 OAuth2 앱 접근 불가(공식). ② **콘텐츠 조회**(pages/spaces/restrictions) = **admin OAuth accessToken + Bearer + 게이트웨이**(`/ex/confluence/{cloudId}/...`) + Admin Key 헤더. 근거·실측(404→200)은 Feature 0 §검증 노트, scope 셋은 §구현 시 주의. 보관: ① `admin_atlassian_credential`(`site_url`+`admin_api_token_enc`, `V004`/§6.4), ② `user_tokens`(OAuth+`cloud_id`). #6 확정 2026-06-11 | 2026-06-10 (하이브리드·#6 06-11) |
| FE 진입점·gateway 라우팅 | **BFF 단일 진입점**. `/api/auth/**` 는 BFF gateway **path-through** 로 auth-server 의 `/api/auth/*` 핸들러에 위임((a) 안 확정). `/api/users/me`·`/api/admin/*` 는 BFF→MySQL 직접 읽기. auth-server 추가 노출면=`/internal/*`(credential·admin-key). MySQL 은 3단계부터 공유(auth-server 쓰기, BFF 읽기) | 2026-06-09 |
| groups/cloudId 취득 경로 | **Atlassian 공식 API 로 별도 취득**(토큰 디코딩 아님). **cloudId**=AUTH-04 `accessible-resources` 의 `id`(멀티사이트 순서 없음·최근 인가 추론 금지). **groups**=AUTH-05 `GET /ex/confluence/{cloudId}/wiki/rest/api/user/memberof?accountId=` 의 `results[].id`(=**`groupId`**, `name` 아님 — RAG `allowed_groups` 정합, 2026-06-09 확정. 페이지네이션 `start`/`limit` 200/`totalSize`). callback 에서 조회해 JWT `groups` claim 적재. **`user_space_acl` RDB 테이블 미사용** — 페이지-단위 ACL 은 수집단계 Qdrant payload(`allowed_groups`/`allowed_users`, ADR 0001 §2). 페이지 ACL 추출은 Confluence Content restrictions API(수집측). | 2026-06-09 |
| role 결정 정책 | **MySQL `users.role` DB 단일 source** — OAuth callback 시 accountId lookup, 행 없으면 `role='USER'` INSERT, 행 있으면 그 값 사용. JWT `role` claim 은 그 값 그대로 발급. YAML bootstrap 미사용. **최초 admin** 은 Flyway `V001` 하드코딩 INSERT. 별도 `admins` 테이블 미사용(흡수). (`docs/db-schema.md` §6.1) | 2026-06-02 |
| Admin Key activate TTL·요청 계약 | activate 는 `durationInMinutes` **60분(최대값)** 으로 요청(설정 외부화) — BFF 운영 문서(4단계 Feature 1)의 "60분 TTL = 최종 fallback" 전제 정합, 기본 10분이면 장기 수집 중 키 만료 → 제한 페이지 silent 404 누락. 요청 body 는 activate/deactivate 모두 `{ adminUserId, jobId }`(PR #21 선머지된 BFF `AuthAdminKeyClient` 계약). deactivate 중복 `jobId` 는 Atlassian DELETE 재호출 없이 2xx(in-memory jobId store, 단일 인스턴스 전제) | 2026-06-12 |
| (예정) Refresh Token 갱신 정책 | LINA 세션 refresh = Rotating(Feature 4). Confluence refresh = AUTH-03 Rotating(Feature 5). 저장/TTL 세부는 Feature 2/4 착수 시 확정 | — |
