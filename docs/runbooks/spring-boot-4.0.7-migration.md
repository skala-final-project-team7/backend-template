# Spring Boot 4.0.7 마이그레이션 런북 (1차 공통 기반)

## 목표

`backend/` 멀티모듈 공통 기반을 Spring Boot 3.x에서 4.0.7로 1차 전환한다.

## 범위

- `backend/` 하위 Gradle 멀티모듈 빌드 스크립트
- 공통 버전/의존성 관리 정책
- 설정/문서의 기본 호환성 표기(최소 한계치 정리)

### 제외

- 인증/인가, RAG, 배치/스케줄링, CI/CD, 운영 스크립트까지의 고도화는 2차 적용.

## Feature 1 — 공통 빌드 및 BOM 정합성

- [x] `backend/build.gradle`
  - `org.springframework.boot` 플러그인: `3.3.5 -> 4.0.7`
  - `springCloudVersion`: `2023.0.3 -> 2025.1.1` (Boot 4.0.x 정합성 기준)
- [x] `backend/common/build.gradle`
  - `spring-boot-dependencies` BOM: `3.3.5 -> 4.0.7`
- [x] `backend/bff-server/build.gradle`
  - Spring Cloud BOM 변수를 Boot 4 기준으로 참조
  - 임베디드 MongoDB 충돌 대응 주석 갱신

### Feature 1 테스트

- 실행 명령
  - `cd backend && ./gradlew :common:clean :common:test`
  - `cd backend && ./gradlew :common:dependencies --configuration runtimeClasspath | rg "spring-boot-dependencies"`

## Feature 2 — 문서 정합성 1차 정리

- [x] `backend/CLAUDE.md` (BFF 구성 스택 표기)
- [x] `docs/architecture.md` (Backend 스택 표기)
- [x] `docs/conventions.md` (호환성 기준 표기)
- [x] `backend/bff-server/current-plans.md` (초기 스택 표기)

### Feature 2 테스트(문서 검증)

- 실행 명령
  - `rg -n "Spring Boot 3\\.x|Spring Boot 3\\.3\\.x|2023\\.0\\.3" backend docs`

## Feature 3 — 호환성 점검 산출물 준비

- [ ] `Backend 공통 코드`에서 Boot 4.x에서 제거 가능성이 높은 API/속성 후보 목록 추출
  - `rg -n "WebSecurityConfigurerAdapter|antMatchers\\(|authorizeRequests\\(|bootstrap\\.yml|WebMvcConfigurerAdapter|javax\\.sql\\.|javax\\.persistence|jakarta\\.validation\\." backend`
  - 결과 저장: `docs/runbooks/spring-boot-4.0.7-migration.md` 에 업데이트 예정

### Feature 3 테스트

- 실행 명령(분석용)
  - `cd backend && ./gradlew test --tests "*Compatibility*"`

## Feature 4~8 로드맵

- Feature 4: Java/Groovy/Kotlin toolchain 호환성 및 플러그인 정합성 확인
- Feature 5: Deprecated/제거 API 정리 및 수정
- Feature 6: `application*.yml` 설정 키 정합성 점검
- Feature 7: 전체 테스트/핵심 플로우 점검 (인증/결제/트랜잭션/배치/스케줄링)
- Feature 8: Docker/CI/배포/모니터링/롤백 플랜 문서 확정

각 Feature는 위 1~3단계와 동일한 방식으로, 기능 단위로 반영 후 `전체 테스트`를 수행한다.

## 롤백 플랜 (1차)

- 기준 커밋: Spring Boot 4.0.7 반영 이전 체크포인트
- 롤백 기준:
  - `backend/build.gradle` 또는 공통 모듈(BOM) 테스트 실패가 `Feature 1`에서 재현되는 경우
  - 공통 API/설정 스키마 회귀로 주요 기능(인증/채팅/관리자 API) 실패가 재현되는 경우
- 롤백 절차:
  1. `backend/build.gradle`, `backend/common/build.gradle` 버전 값을 이전값으로 즉시 복귀
  2. 관련 플러그인/템플릿 캐시 제거(필요 시 `./gradlew --stop`, `.gradle` clean)
  3. 해당 커밋/체크포인트로 재배포 후 Feature 단위 재적용
