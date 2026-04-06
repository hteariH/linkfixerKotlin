FROM ubuntu:25.04

# Set the working directory in the container
WORKDIR /app

# Install required packages in a single RUN command to reduce layers
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-17-jdk \
        yt-dlp \
        ffmpeg \
        dos2unix \
        && apt-get clean \
        && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Copy the Gradle build files first for better caching
COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Normalize line endings and make the gradlew script executable (fixes /bin/sh: not found on Linux when CRLF present)
RUN dos2unix ./gradlew && chmod +x ./gradlew

# Download dependencies (this layer will be cached)
RUN ./gradlew dependencies --no-daemon

# Copy the source code
COPY src ./src

# Build the application
RUN ./gradlew build --no-daemon

# Set environment variables
ENV DOWNLOADS_DIR="/data/downloads"
# Create a downloads directory for storing cached videos
RUN mkdir -p $DOWNLOADS_DIR

# Set the entry point to run the application
ENTRYPOINT ["java", "-jar", "/app/build/libs/hydra-manager-bot-1.0-SNAPSHOT.jar"]