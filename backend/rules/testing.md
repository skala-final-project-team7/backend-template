# Backend - 테스트 규칙

`backend/CLAUDE.md`와 함께 적용한다. 공통 테스트 규칙은 `docs/conventions.md` 12장을 따른다. 이 문서는 Backend 고유 테스트 규칙을 다룬다.

> 이 문서에서 언급하는 `CLAUDE.md`, `docs/...` 경로는 모두 **프로젝트 루트** 기준이다.

---

## 1. 테스트 범위

| 대상 | 테스트 유형 | 필수 여부 |
|---|---|---|
| Service 비즈니스 로직 | Unit Test | 필수 |
| Controller API 계약 | MockMvc / WebMvcTest | 필수 |
| Repository 쿼리 | `@DataMongoTest`(bff-server MongoDB) / `@DataJpaTest`(auth-server MySQL) | 주요 쿼리에 한해 |
| Client 외부 호출 | WireMock / MockServer | ML Pipeline, Confluence 호출에 한해 |
| 인증 흐름 | SecurityTest | 필수 |

---

## 2. 테스트 환경 규칙

- 테스트에서 실제 Confluence API, ML Pipeline을 호출하지 않는다. Mock을 사용한다.
- 테스트에서 인증을 비활성화할 때는 `@WithMockUser` 또는 Test Security Config를 사용한다. Production Security Config를 수정하지 않는다.
- 테스트 데이터베이스는 H2 또는 Testcontainers를 사용한다.
