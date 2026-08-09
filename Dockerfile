# Multi-stage Spring Boot image (local docker compose + hosted Docker runtimes).
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
RUN mvn -B -q --no-transfer-progress dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -B -q --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl \
    && addgroup -S spring -g 1000 \
    && adduser -S spring -u 1000 -G spring
COPY --from=build /app/target/novora_backend-*.jar /app/app.jar
RUN chown spring:spring /app/app.jar
USER spring
EXPOSE 8080
# Default Spring port 8080; docker-compose overrides SERVER_PORT=8081 and its own healthcheck.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/liveness >/dev/null || exit 1
# Prefer a larger heap fraction in small containers (override with JAVA_OPTS).
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError $JAVA_OPTS -jar /app/app.jar"]
