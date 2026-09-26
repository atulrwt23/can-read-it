# syntax=docker/dockerfile:1
# CanReadIt API. Built natively per architecture (linux/amd64, linux/arm64).
# Base images are pinned to their major version so rebuilds pick up security patches.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src/main src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet bootJar \
 && java -Djarmode=tools -jar build/libs/can-read-it-backend-*.jar extract --layers --destination /app

FROM eclipse-temurin:25-jre
WORKDIR /app
# Layers ordered from least to most frequently changing, for smaller pulls on deploy.
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# No curl in the JRE image: ask the readiness probe over bash's /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=4 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/readiness HTTP/1.0\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'
ENTRYPOINT ["java", "-jar", "can-read-it-backend-0.1.0-SNAPSHOT.jar"]
