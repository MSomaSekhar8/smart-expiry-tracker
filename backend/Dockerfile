# syntax=docker/dockerfile:1
# Multi-stage build: Maven (JDK 21) -> slim JRE 21 runtime.
# Tests run in CI/locally, not inside the image (hence -DskipTests).
#
# Runtime configuration is supplied by Render as environment variables
# (DB_URL, DB_PASSWORD, JWT_SECRET, PORT, ...) — nothing is hardcoded here.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
# The single repackaged Spring Boot jar (version-agnostic glob).
COPY --from=build /build/target/*.jar app.jar
USER app
# Informational only — the app binds ${PORT} from Render
# (application.yml: server.port=${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]