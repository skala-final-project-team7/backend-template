# Working Log

> 작업 완료 후 핵심 변경 사항과 결정을 일자별로 누적 기록한다.
> 각 모듈의 진행 중 Plan(체크리스트)은 `backend/<module>/current-plans.md` 를 참조한다.

---

## 2026-05-22

- **Confluence 토큰 전달 대상 변경 (`/ml/query` → `/ml/ingest`)**: 토큰(`accessToken`/`cloudId`) 을 RAG 질의 본문에서 제거하고 Data Ingestion 호출 본문에만 첨부하도록 일괄 갱신. **근거**: 권한은 수집 시 Qdrant payload(`allowed_groups`/`allowed_users`) 에 ACL 저장 + 질의 시 JWT 의 `user_id`/`groups` 로 필터링 (기획서 §6.4/§6.6). 따라서 `/ml/query` 는 라이브 Confluence 호출이 없어 토큰 불필요. **전제**: `/ml/query` 가 실시간 Confluence 호출을 일절 안 한다고 가정 (※ ML 확인 대기). 변경 파일: `docs/api-spec.md` §2-1/§2-2 (토큰 필드·보안 박스 이동), `backend/bff-server/current-plans.md` 확정 결정 #5 + Feature 5 체크리스트.
- **Data Ingestion 클라이언트 키 정합화**: `bff-server` 설정·빈 이름을 그림(아키텍처)의 박스명과 1:1 로 일치시킴 — `application.yml` 의 두 번째 ML 클라이언트 블록 이름을 `data-ingestion` 으로 명확화 (관련 env: `DATA_INGESTION_BASE_URL`/`DATA_INGESTION_TIMEOUT_MS`), `RagClientConfig` 의 두 번째 RestClient 빈 이름을 `dataIngestionRestClient` 로 정렬, 테스트(`RagClientConfigTest`) 검증 빈 이름·DisplayName 동기화. base-url fallback 동작은 기존과 동일(`${ML_PIPELINE_BASE_URL:}` 폴백 유지) — 이름만 변경. 빈은 4단계 `/api/admin/ingest` 도입 전까지 미사용 placeholder.

## 2026-05-21

- **팀 피드백 수신·문서 반영**: 3가지 사항을 관련 문서에 반영.
  - **Confluence OAuth → ML 서버 토큰 전달**: 3단계에서 **PoC 모드(`accessToken` + `cloudId` 직접 전달) 우선** 구현, 후속 라운드에서 DB 기반 `connectionId` 로 확장 (`backend/auth-server/current-plans.md` §3단계 Feature A~D, 확정된 결정 #5). ※ 토큰 전달 대상 엔드포인트는 2026-05-22 자로 `/ml/ingest` 로 조정 (당일 working-log 항목 참조).
  - **응답 timestamp 정책**: 저장은 UTC, 응답 JSON 은 KST(`+09:00`) 절대 전환. `docs/api-spec.md` 응답 예시 15건 일괄 갱신, bff-server Feature 3/4/5/6 체크리스트에 KST 직렬화 항목 추가 (확정된 결정 #6).
  - **ML 클라이언트 / Confluence 설정 키 추가**: `bff-server/application.yml` 에 두 번째 ML 클라이언트 블록 및 `lina.confluence.*` 환경변수 참조 추가 (평문 secret 없음). ※ 두 번째 ML 클라이언트 블록 이름은 2026-05-22 자로 Data Ingestion 클라이언트로 정합화 (당일 항목 참조).
- **보안 운영 규칙 합의**: PoC 모드 한정 — 로그·tracing 본문 마스킹, RabbitMQ payload 토큰 미포함, actuator `env`/`heapdump` 비노출, ML Pod NetworkPolicy 적용. `docs/api-spec.md` 보안 주의 박스에 명시 (대상 엔드포인트는 2026-05-22 자 항목 참조).
- **Health check 범위 확인**: BFF 자체 `/actuator/health` 이미 노출, ML 서버 health 는 `docs/api-spec.md` §2-4 정의 + Resilience4j 격리 정책 유지. Data Ingestion / RAG 세부 indicator 는 영훈·태성 담당으로 분리.

## 2026-05-20

- **저장소 전환**: 2단계 BFF 도메인 영속을 MySQL/JPA → MongoDB/Spring Data MongoDB 로 전환. `message_sources` 별도 컬렉션 → `messages.sources` 내장 배열. `backend/CLAUDE.md` §2.2/§6/§7, `docs/db-schema.md`, `docs/architecture.md` §3/§6, `backend/bff-server/current-plans.md` 확정된 결정 #4 갱신.
- **디렉토리 구조 정리**: Gradle 멀티모듈 root 를 `backend/` 하위로 이동. 모듈별 `CLAUDE.md` / `current-plans.md` 분리. `docs/ai/working-log.md`, `docs/adr/` 신규 도입. `docs/ai/current-plan.md` → `backend/bff-server/current-plans.md` 로 이관.

## 2026-05-19

- **2단계 Feature 1 완료**: 영속 계층 + DB 스키마 초안. 처음에는 MySQL/JPA + H2 로 구현 (테스트 7건 통과). 인증 부재 격리·데모 사용자 설정값(`lina.demo.*`) 도입.
- **2단계 Plan 작성**: bff-server 핵심 API 의 Feature 1~7 체크리스트와 인증 부재 처리 방침(`DemoSecurityConfig` + `CurrentUserProvider` 격리) 확정. 피드백 upsert(메시지당 1건, 신규 201/갱신 200), soft delete, 데모 사용자 설정값 결정.
- **`docs/api-spec.md` §4-3 추가**: Confluence 페이지 미리보기 (5주차 이후, 인증 의존, 호출 주체 TBD).

## 2026-05-15

- **1단계 완료**: Gradle 멀티모듈 골격(`common`, `bff-server`, `auth-server`), 공통 `ApiResponse`/`ErrorCode`/`GlobalExceptionHandler` 도입. checkstyle + spotless(google-java-format) 적용.
