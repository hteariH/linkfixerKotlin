FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew build --no-daemon -x test

RUN mkdir -p /data/logs

ENTRYPOINT ["java", "-jar", "/app/build/libs/grokbot-1.0-SNAPSHOT.jar"]
