# Spring Boot 4.0.7 마이그레이션(남은 작업 정리)

## 목적
Spring Boot 3.x → Spring Boot 4.0.7 1차 전환 범위(공통 기반/빌드/설정/실행흐름) 완수 후 실패 시 롤백 가능한 방식으로 안정적으로 확장한다.

## 현재 상태
- `backend/build.gradle`, `backend/common/build.gradle`, `backend/auth-server/src/main/java/com/lina/auth/config/JwtAuthenticationFilter.java`가 작업 중 수정 상태로 남아 있음.
- `docs/ai/working-log.md`에는 기존 회귀 대응 이력(내부 API 키 필터 회귀, 테스트 결과)이 기록되어 있음.

## 다음 세션에서 남은 작업(1~8)

1. [ ] **Spring Boot 버전 상향 일괄 정합**
   - `backend/build.gradle` 중심으로 모든 모듈이 `springBootVersion=4.0.7` 기준으로 빌드되도록 점검.
   - 기존 모듈별 하드코딩된 Boot 버전, dependencyManagement 선언, plugin 버전 상수 충돌 정리.
   - 문서: `docs/ai/spring-version-migration.md`에 실제 적용 결과(변경 라인/이유) 기록.

2. [ ] **Gradle 플러그인·BOM·의존성 정렬**
   - `plugins` 블록(`org.springframework.boot`, `io.spring.dependency-management`, kotlin/groovy plugin) 호환 버전 정리.
   - `spring-boot-dependencies` BOM, `spring-boot-starter*`, `spring-cloud` 계열의 버전 라인 일치.
   - `./gradlew dependencies`/`dependencyInsight`로 충돌/이중 의존성 체크.

3. [ ] **Java/JVM 및 툴체인 정합성**
   - JDK 버전 업그레이드 필요 유무(컴파일/테스트/도커 런타임 포함) 확인.
   - `sourceCompatibility`, `targetCompatibility`, `kotlin` 버전, `toolchain` 설정 일관성 점검.
   - Gradle wrapper/CI runner JDK 버전 대응 확인.

4. [ ] **deprecated/삭제 API 및 설정 키 정리**
   - Spring Boot 4 전환으로 동작 변경된 설정 키, 리디렉션, Security/Validation/Jackson/RestClient/Cache 설정을 전수 점검.
   - 코드를 실행단에서 `@Deprecated` 경고 중심으로 정리.
   - `JwtAuthenticationFilter` 등 보안 필터/컨텍스트 처리 경로를 Boot 4 기준으로 2차 검토.

5. [ ] **application.yml/hyphen 표준 검토**
   - `application.yml`, `application-*.yml`의 프로필별 기본값·키명 변경/이슈 정리.
   - actuator, datasource, jpa, logging, jwt/security 관련 기본값 변경 여부 반영.
   - 실제 운영/로컬 env 주입 값과 문서화된 값의 drift 비교.

6. [ ] **테스트 코드 전체 통과 검증**
   - 모듈별 테스트를 feature 단위로 순차 실행:
     - `:common:test`
     - `:auth-server:test`
     - `:bff-server:test`
     - 필요 시 `-Dspring.profiles.active=test` 스위칭
   - 실패 시 “항목별 수정 → 테스트 → 커밋” 단위로 재수행.

7. [ ] **주요 플로우 수동 점검**
   - 인증/OAuth, 결제·트랜잭션(해당 모듈 기준 결제/결제성 액션), 배치/스케줄링 핵심 시나리오 smoke test.
   - 외부 연동이 필요한 플로우는 dev/local env에서 최소 1회 통과 기록.

8. [ ] **Docker/CI 파이프라인 검증**
   - Dockerfile/Compose/CI script에서 JDK/Gradle/이미지 태그 호환성 확인.
   - `bootBuildImage` 또는 image build 테스트.
   - 배포 스크립트(rollout/health gate)에서 4.x 동작 확인.

## 선택 항목(필수는 아님, 다음 단계로 연계)
- [ ] 모니터링·헬스체크·메트릭 정상 확인 (actuator/health/readiness/liveness 지표 비교)
- [ ] 롤백 플랜 문서화 (이슈 # 기준으로 revert/커밋 포인트/배포 중단 조건 정의)

## 실행 시 기록 규칙(요청 반영)
- 오류 1건 해결 단위마다 1개 커밋.
- 커밋 메시지는 한글 권장.
- 테스트 실행 결과는 `docs/ai/working-log.md`에 실시간으로 추가.
