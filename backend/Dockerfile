# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build

ARG SERVICE_MODULE

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    case "${SERVICE_MODULE}" in \
      virtualrift-gateway|virtualrift-auth|virtualrift-tenant|virtualrift-orchestrator|virtualrift-reports|virtualrift-web-scanner|virtualrift-api-scanner|virtualrift-network-scanner|virtualrift-sast) ;; \
      *) echo "Unsupported SERVICE_MODULE: ${SERVICE_MODULE}" >&2; exit 1 ;; \
    esac \
    && mvn -B -ntp -pl "${SERVICE_MODULE}" -am package -DskipTests \
    && artifact=$(find "${SERVICE_MODULE}/target" -maxdepth 1 -type f -name '*-exec.jar' -print -quit) \
    && test -n "$artifact" \
    && cp "$artifact" /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_PORT=8080

RUN addgroup -S -g 10001 virtualrift \
    && adduser -S -D -H -u 10001 -G virtualrift virtualrift

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/application.jar /app/application.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_OUTPUT_ANSI_ENABLED=NEVER

USER 10001:10001

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
