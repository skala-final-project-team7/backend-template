# Backend Template Docker Build Guide

`backend-template/Dockerfile`은 하나의 파일로 `auth-server`와 `bff-server`를 각각 빌드할 수 있게 구성되어 있습니다.
각 서버는 별도 타깃(`--target`)으로 이미지를 생성합니다.

## 1) 기본 빌드(로컬)

- **auth-server** (포트 `8081`)

```bash
docker build \
  -f backend-template/Dockerfile \
  --target auth-server \
  -t lina/auth-server:latest \
  backend-template
```

- **bff-server** (포트 `8080`)

```bash
docker build \
  -f backend-template/Dockerfile \
  --target bff-server \
  -t lina/bff-server:latest \
  backend-template
```

## 2) 레지스트리 태그 포함 빌드 & 푸시

- **auth-server**

```bash
docker build \
  -f backend-template/Dockerfile \
  --target auth-server \
  -t <REGISTRY>/lina/auth-server:latest \
  backend-template

docker push <REGISTRY>/lina/auth-server:latest
```

- **bff-server**

```bash
docker build \
  -f backend-template/Dockerfile \
  --target bff-server \
  -t <REGISTRY>/lina/bff-server:latest \
  backend-template

docker push <REGISTRY>/lina/bff-server:latest
```

## 3) 환경별 태그 예시

```bash
TAG=<VERSION_OR_GIT_SHA>

docker build -f backend-template/Dockerfile --target auth-server \
  -t <REGISTRY>/lina/auth-server:${TAG} backend-template
docker build -f backend-template/Dockerfile --target bff-server \
  -t <REGISTRY>/lina/bff-server:${TAG} backend-template

docker push <REGISTRY>/lina/auth-server:${TAG}
docker push <REGISTRY>/lina/bff-server:${TAG}
```

## 4) 주의사항

- `Dockerfile`에서 `BUILD` 타깃은 `:auth-server:bootJar`, `:bff-server:bootJar`를 모두 수행합니다.
- 실행 포트가 고정되어 있습니다.
  - `auth-server`: `8081`
  - `bff-server`: `8080`
- 실행 환경 프로필은 기본 `prod`이며, 필요 시 런타임에서 `SPRING_PROFILES_ACTIVE`를 덮어쓸 수 있습니다.

예:

```bash
docker run -e SPRING_PROFILES_ACTIVE=prod <IMAGE>
```
