# Backend Template Docker Build Guide

`backend/Dockerfile.auth`와 `backend/Dockerfile.bff`로 `auth-server`와 `bff-server`를 각각 독립적으로 빌드합니다.

## 1) 기본 빌드(로컬)

- **auth-server** (포트 `8081`)

```bash
docker build \
  -f backend/Dockerfile.auth \
  -t lina/auth-server:latest \
  backend
```

- **bff-server** (포트 `8080`)

```bash
docker build \
  -f backend/Dockerfile.bff \
  -t lina/bff-server:latest \
  backend
```

## 2) 레지스트리 태그 포함 빌드 & 푸시

- **auth-server**

```bash
docker build \
  -f backend/Dockerfile.auth \
  -t <REGISTRY>/lina/auth-server:latest \
  backend

docker push <REGISTRY>/lina/auth-server:latest
```

- **bff-server**

```bash
docker build \
  -f backend/Dockerfile.bff \
  -t <REGISTRY>/lina/bff-server:latest \
  backend

docker push <REGISTRY>/lina/bff-server:latest
```

## 3) 환경별 태그 예시

```bash
TAG=<VERSION_OR_GIT_SHA>

docker build \
  -f backend/Dockerfile.auth \
  -t <REGISTRY>/lina/auth-server:${TAG} \
  backend
docker build \
  -f backend/Dockerfile.bff \
  -t <REGISTRY>/lina/bff-server:${TAG} \
  backend

docker push <REGISTRY>/lina/auth-server:${TAG}
docker push <REGISTRY>/lina/bff-server:${TAG}
```

## 4) 주의사항

- 각 서비스 Dockerfile은 해당 서비스만 빌드합니다.
- 실행 포트가 고정되어 있습니다.
  - `auth-server`: `8081`
  - `bff-server`: `8080`
- 실행 환경 프로필은 기본 `prod`이며, 필요 시 런타임에서 `SPRING_PROFILES_ACTIVE`를 덮어쓸 수 있습니다.

예:

```bash
docker run -e SPRING_PROFILES_ACTIVE=prod <IMAGE>
```
