# syntax=docker/dockerfile:1.7

# [A1] BuildKit 캐시를 최대한 활용하기 위해
#      - 자주 안 바뀌는 빌드 설정을 먼저 복사
#      - 의존성/Gradle 캐시 레이어와 소스 레이어를 분리
#      - 자주 바뀌는 소스 변경이 전체 빌드 캐시를 다 날리지 않도록 구성
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# [A2] 빌드 설정만 먼저 복사: 변경 빈도 낮은 레이어
#      이 레이어가 캐시되면 소스 변경이 있어도 gradle 래퍼/의존성 resolution 단계는 재활용됨
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle common/
COPY auth-server/build.gradle auth-server/
COPY bff-server/build.gradle bff-server/

# [A3] 소스 이전 의존성 계산: 코드/리소스 변경 없이도 재사용될 캐시 레이어를 만듦
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew \
    && ./gradlew :auth-server:dependencies :bff-server:dependencies --no-daemon

# [A4] 소스 복사 단계 분리: 실제 변경이 많은 부분만 아래 레이어에서만 invalidate
COPY common ./common
COPY auth-server ./auth-server
COPY bff-server ./bff-server

# [A5] 소스까지 반영한 실제 빌드 (Gradle 캐시 마운트로 로컬 의존성 재활용)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :auth-server:bootJar :bff-server:bootJar --no-daemon

# [B1] 공통 런타임 베이스로 중복 단계 줄이기
FROM eclipse-temurin:21-jre AS runtime-base
WORKDIR /app
RUN addgroup --system app \
  && adduser --system --ingroup app app

FROM runtime-base AS auth-server
COPY --from=build /workspace/auth-server/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--spring.profiles.active=prod"]

FROM runtime-base AS bff-server
COPY --from=build /workspace/bff-server/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--spring.profiles.active=prod"]
