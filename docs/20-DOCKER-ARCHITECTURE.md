# 20 — Docker Architecture

## Layout

```
backend/
├── Dockerfile          # Multi-stage production image
└── .dockerignore       # Excludes target/ and .git/
```

Note: an earlier docker-compose / nginx setup (root `Dockerfile`,
`docker-compose.yml`, `nginx.conf`, two `.dockerignore` files) was removed by
project decision — the deployment is a single container behind Render's TLS
termination.

## `backend/Dockerfile`

```dockerfile
# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline          # pre-fetch dependencies
COPY src ./src
RUN mvn -B -ntp -DskipTests package            # jar: pantry-tracker-backend-0.1.0.jar
# build cache mount (used in the actual file):
#   --mount=type=cache,target=/root/.m2

# Stage 2 — runtime
FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin app   # non-root user
USER app
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
EXPOSE 8080             # informational — the app binds ${PORT:8080}
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

(Container-specific details above reflect the file's structure; the exact
instruction ordering is in `backend/Dockerfile`.)

## Properties

- **Non-root runtime user** (`USER app`) — container hygiene.
- **Two stages** — build tools (Maven + JDK) are not in the runtime image;
  only the JRE + jar ship.
- **Layer/build caching** — the Maven repository is cached across builds
  (`--mount=type=cache,target=/root/.m2`), so CI rebuilds are fast.
- **No secrets baked in** — all configuration comes from environment
  variables at runtime.
- **Port** — the app binds `server.port=${PORT:8080}`; Render injects
  `PORT`. `EXPOSE 8080` is informational only.

## `.dockerignore`

```dockerignore
target/
.git/
```

Keeps the build context small (no compiled classes, no VCS history).

## Verified behavior

- `docker build` succeeded (image `pantry-backend-test`).
- Local run against a throwaway Postgres container on `PORT=9099`:
  Flyway applied V1–V4, Tomcat started on 9099, `GET /api/health` → UP.
- Test containers and network were cleaned up afterwards.

## Build vs runtime

| Stage | Base image | Contents |
|---|---|---|
| build | `maven:3.9-eclipse-temurin-21` | Maven, JDK 21, sources, `~/.m2` cache |
| runtime | `eclipse-temurin:21-jre` | JRE 21, jar, non-root `app` user |