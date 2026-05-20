# auth-server Current Plans

> `auth-server` 모듈의 현재 진행 중인 Plan. 세션 간 인수인계용 (`docs/ai/workflow.md` §6.3).
> 모듈 전용 규칙은 `backend/auth-server/CLAUDE.md` 참조.

---

## 진행 상태 요약

| 단계 | 범위 | 상태 |
|---|---|---|
| 1단계 | 프로젝트 초기 셋업 (패키지 구조, 설정, 공통 응답/예외) | ✅ 완료 (2026-05-15, `backend/bff-server/current-plans.md` 1단계 공동 작업) |
| 3단계 | Confluence OAuth 2.0 Authorization Code Flow + Access/Refresh Token 암호화 저장 + JWT 발급 | 📝 미착수 |

---

# 3단계 — Authorization Server

> 착수 전 Plan 을 본 파일에 작성한다. 참조: `backend/rules/auth.md`, `docs/api-spec.md` §4-1.

## 작업 요약

(미정 — Plan 단계에서 작성)

## 구현 Feature 및 체크리스트

(미정 — Plan 단계에서 작성)

---

## 확정된 결정

| 항목 | 결정 | 일자 |
|---|---|---|
| (예정) JWT 서명 알고리즘 | TBD | — |
| (예정) Token 암호화 방식 | TBD | — |
| (예정) Refresh Token 갱신 정책 | TBD | — |
