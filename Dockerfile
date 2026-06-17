FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY common ./common
COPY auth-server ./auth-server
COPY bff-server ./bff-server

RUN chmod +x gradlew \
  && ./gradlew :auth-server:bootJar :bff-server:bootJar --no-daemon

FROM eclipse-temurin:21-jre AS auth-server

WORKDIR /app

RUN addgroup --system app \
  && adduser --system --ingroup app app

COPY --from=build /workspace/auth-server/build/libs/*.jar /app/app.jar

USER app
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]

FROM eclipse-temurin:21-jre AS bff-server

WORKDIR /app

RUN addgroup --system app \
  && adduser --system --ingroup app app

COPY --from=build /workspace/bff-server/build/libs/*.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
