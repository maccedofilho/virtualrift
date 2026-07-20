# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build

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

FROM eclipse-temurin:25-jre-noble@sha256:2f1da100788559b397bcf48c736169ea5b070bde84e55f203bbee8e83d87a175

ARG SERVICE_PORT=8080

RUN groupadd --gid 10001 virtualrift \
    && useradd --uid 10001 --gid virtualrift --no-create-home \
      --shell /usr/sbin/nologin virtualrift

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/application.jar /app/application.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_OUTPUT_ANSI_ENABLED=NEVER

USER 10001:10001

EXPOSE ${SERVICE_PORT}

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
