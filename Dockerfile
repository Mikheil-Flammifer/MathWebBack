# syntax=docker/dockerfile:1.7

# ---------- Stage 1: build ----------
FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build

WORKDIR /build

# Copy dependency definitions first so Docker can cache Maven downloads.
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src

# Tests run in CI. The image build only packages the application.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
# Non-Alpine image: jave-all-deps needs glibc.
FROM eclipse-temurin:21-jre@sha256:d77e090ae1f6970c047b443c6f0a6c1358b0426d44432bb5a7753408cd43de0d AS runtime

RUN groupadd --system app \
    && useradd --system --gid app --create-home app

WORKDIR /app

COPY --from=build /build/target/mathweb-*.jar app.jar

RUN mkdir -p /app/uploads/videos /app/uploads/images \
    && chown -R app:app /app

USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    FILE_UPLOAD_PATH=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

VOLUME ["/app/uploads"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]