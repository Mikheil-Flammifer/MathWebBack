# syntax=docker/dockerfile:1.7
ARG JAVA_VERSION=23

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /build

# Copy pom first so dependency downloads are cached between builds
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Tests already run in CI, so skip them here
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
# Not Alpine: jave-all-deps bundles ffmpeg binaries that need glibc
FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime

RUN groupadd --system app && useradd --system --gid app --create-home app

WORKDIR /app
COPY --from=build /build/target/mathweb-*.jar app.jar

RUN mkdir -p /app/uploads/videos /app/uploads/images \
    && chown -R app:app /app

USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    FILE_UPLOAD_PATH=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
VOLUME /app/uploads

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]