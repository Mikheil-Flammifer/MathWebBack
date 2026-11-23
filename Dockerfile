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
FROM eclipse-temurin:24-jre@sha256:8cb2387a28af84cf0db0948d9c67d4480192f4e567027a3963f145d218e8b4f2 AS runtime

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