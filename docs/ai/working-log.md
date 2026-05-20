# Working Log

> 작업 완료 후 핵심 변경 사항과 결정을 일자별로 누적 기록한다.
> 각 모듈의 진행 중 Plan(체크리스트)은 `backend/<module>/current-plans.md` 를 참조한다.

---

## 2026-05-20

- **저장소 전환**: 2단계 BFF 도메인 영속을 MySQL/JPA → MongoDB/Spring Data MongoDB 로 전환. `message_sources` 별도 컬렉션 → `messages.sources` 내장 배열. `backend/CLAUDE.md` §2.2/§6/§7, `docs/db-schema.md`, `docs/architecture.md` §3/§6, `backend/bff-server/current-plans.md` 확정된 결정 #4 갱신.
- **디렉토리 구조 정리**: Gradle 멀티모듈 root 를 `backend/` 하위로 이동. 모듈별 `CLAUDE.md` / `current-plans.md` 분리. `docs/ai/working-log.md`, `docs/adr/` 신규 도입. `docs/ai/current-plan.md` → `backend/bff-server/current-plans.md` 로 이관.

## 2026-05-19

- **2단계 Feature 1 완료**: 영속 계층 + DB 스키마 초안. 처음에는 MySQL/JPA + H2 로 구현 (테스트 7건 통과). 인증 부재 격리·데모 사용자 설정값(`lina.demo.*`) 도입.
- **2단계 Plan 작성**: bff-server 핵심 API 의 Feature 1~7 체크리스트와 인증 부재 처리 방침(`DemoSecurityConfig` + `CurrentUserProvider` 격리) 확정. 피드백 upsert(메시지당 1건, 신규 201/갱신 200), soft delete, 데모 사용자 설정값 결정.
- **`docs/api-spec.md` §4-3 추가**: Confluence 페이지 미리보기 (5주차 이후, 인증 의존, 호출 주체 TBD).

## 2026-05-15

- **1단계 완료**: Gradle 멀티모듈 골격(`common`, `bff-server`, `auth-server`), 공통 `ApiResponse`/`ErrorCode`/`GlobalExceptionHandler` 도입. checkstyle + spotless(google-java-format) 적용.
