# syntax=docker/dockerfile:1

# ---- Build stage -------------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app

# Resolve dependencies first so they are cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI / locally via `mvn clean verify`; skipped here to keep deploys fast
RUN mvn -B -q package -DskipTests && cp target/dispatch-load-balancer-*.jar app.jar

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Run as an unprivileged user
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/app.jar app.jar
USER app

# Size the heap from the container's memory limit (Render free instances have 512 MB)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
