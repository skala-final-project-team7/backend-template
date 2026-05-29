# Backend - 인증/인가 규칙

`backend/CLAUDE.md`와 함께 적용한다. 인증/인가(Confluence OAuth 2.0, JWT, 토큰 저장/갱신) 관련 작업 시 이 문서를 참조한다.

> 이 문서에서 언급하는 `CLAUDE.md`, `docs/...` 경로는 모두 **프로젝트 루트** 기준이다.

---

## 1. Confluence OAuth 2.0 흐름

```text
사용자 → Frontend → BFF Server → Authorization Server → Confluence
                                                       ↓
                                          Access/Refresh Token 교환
                                          사용자 스페이스 접근 권한 조회
                                          JWT 발급
                                                       ↓
                                  BFF Server ← Authorization Server
                                       ↓
                                  Frontend ← JWT 세션 토큰
```

---

## 2. 인증 관련 규칙

- 별도의 회원가입 로직을 구현하지 않는다. 인증은 Confluence OAuth 2.0에 위임한다.
- Access Token / Refresh Token 원본은 MySQL에 **암호화 저장**한다. 평문 저장 금지.
- JWT에 포함할 Claim: `userId`, `groups`(접근 가능 스페이스/페이지 권한 정보). Claim 이름은 와이어 필드(`/ml/query`)·JSON 표면과 동일하게 **camelCase** 로 통일한다 (`docs/api-spec.md` §2-1, `docs/adr/0001-page-level-acl-source.md` §2.3).
- Refresh Token 기반 자동 갱신을 구현한다. 토큰 만료 전 사전 감지 및 재인증 유도.
- BFF Server는 Authorization Server가 발급한 JWT의 서명, 만료, 권한 Claim만 검증한다.
- BFF Server가 직접 Confluence 토큰을 교환하지 않는다. 반드시 Authorization Server를 통한다.

---

## 3. 인증/인가 금지 사항

- 인증 흐름을 우회하는 코드를 작성하지 않는다.
- 테스트 환경에서 인증을 비활성화할 때는 별도의 Test Security Config를 사용하고, production 코드에 조건 분기를 추가하지 않는다.
- **Confluence OAuth Access/Refresh Token(Atlassian 발급)은 프론트엔드에 노출하지 않는다** — 서버(MySQL)에만 암호화 보관한다. LINA 세션 JWT 는 `Authorization: Bearer` 방식으로 사용하며, 로그인/갱신 응답 `data` 로 access JWT + (LINA 발급) refresh token 을 FE 에 전달한다. **HttpOnly 쿠키는 사용하지 않는다**(`docs/api-spec.md` §4-1).
