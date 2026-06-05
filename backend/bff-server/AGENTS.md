# backend/bff-server/AGENTS.md

이 문서는 Codex 등 Claude 외 에이전트가 `bff-server` 모듈에서 따라야 하는 작업 규칙을 정의한다.

## 적용 규칙

- 기본 규칙은 같은 디렉터리의 `CLAUDE.md`를 따른다.
- 프로젝트 루트 `CLAUDE.md`, `backend/CLAUDE.md`, `docs/architecture.md`, `docs/conventions.md`를 함께 확인한다.
- 작업 착수 전 `backend/bff-server/current-plans.md`의 Feature 목록을 확인한다.
- 작업 유형에 따라 `backend/rules/domains.md`, `backend/rules/rag-pipeline.md`, `backend/rules/auth.md`, `backend/rules/testing.md`를 먼저 확인한다.
- 구현/리뷰/리팩터링 시 `andrej-karpathy-skills/CLAUDE.md`를 보조 개발 원칙으로 적용한다.
- 규칙이 충돌하면 프로젝트/Backend/BFF의 구체 규칙을 Karpathy 보조 원칙보다 우선한다.

## 작업 원칙

- 요청 범위를 넘는 기능, 추상화, 리팩터링을 추가하지 않는다.
- 변경 라인은 작업 목표와 직접 연결되도록 한다.
- 불명확한 가정, 해석 차이, 영향 범위는 구현 전에 드러낸다.
- 완료 기준과 검증 방법을 정하고, 테스트 또는 명령 실행 결과로 확인한다.
