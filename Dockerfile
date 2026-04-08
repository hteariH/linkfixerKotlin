# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first for layer-cached dependency download
COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon -q

# Copy source and build the fat jar
COPY src ./src
RUN ./gradlew build --no-daemon -x test -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/hydra-manager-bot-1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /data

ENTRYPOINT ["java", "-jar", "app.jar"]
