# Use a base image with both Java 17 and Python already installed
FROM eclipse-temurin:17-jdk-jammy

# Set the working directory in the container
WORKDIR /app

# Install required packages in a single RUN command to reduce layers
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        curl \
        ffmpeg \
        && apt-get clean \
        && rm -rf /var/lib/apt/lists/*

# Install yt-dlp directly from GitHub to ensure we get the latest version
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp

# Copy the Gradle build files first for better caching
COPY gradle ./gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Make the gradlew script executable
RUN chmod +x ./gradlew

# Download dependencies (this layer will be cached)
RUN ./gradlew dependencies --no-daemon

# Copy the source code
COPY src ./src

# Build the application
RUN ./gradlew build --no-daemon

# Set environment variables
ENV DOWNLOADS_DIR="/data/downloads"
COPY cookies.txt /cookies.txt
COPY cookies.txt /app/cookies.txt
COPY cookies.txt /data/cookies.txt
# Create a downloads directory for storing cached videos
RUN mkdir -p $DOWNLOADS_DIR

# Set the entry point to run the application
ENTRYPOINT ["java", "-jar", "/app/build/libs/linkfixer-1.0-SNAPSHOT.jar"]