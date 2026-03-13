# LinkFixerBot

LinkFixerBot is a Kotlin-based Telegram bot built with Spring Boot. It provides link fixing and media processing capabilities, along with AI-powered conversation features using Google Gemini AI.

## Features

- **Social Media Link Fixing**: Automatically handles and replaces URLs from platforms like TikTok and Instagram with embed-friendly versions or direct video processing.
- **AI Impersonation**: Uses Google Gemini AI to impersonate specific users based on their message history.
- **Context-Aware Responses**: Remembers previous impersonations using Redis to maintain consistency in conversations.
- **Media Processing**:
    - Video downloading and processing via `yt-dlp` and `ffmpeg`.
    - Voice message transcription using Gemini AI.
    - Image analysis for AI-generated responses.
- **Chat Management**: Multi-bot support (e.g., `LinkFixer_Bot` and `ChatManagerAssistantBot`) and chat-specific settings stored in an H2 database.
- **Admin Forwarding**: Forwards incoming messages to a specified admin chat ID for monitoring.

## Tech Stack

- **Language**: Kotlin 2.1.10
- **Framework**: Spring Boot 3.2.3
- **Database**: H2 (file-based)
- **Caching**: Redis (used for tracking message context)
- **AI**: Google Gemini AI (via `google-genai` library)
- **Deployment**: Docker, Docker Compose, GitHub Actions
- **Tools**: `yt-dlp`, `ffmpeg`, `selenium` (for screenshot generation)

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Google Gemini API Key
- Telegram Bot Tokens (from @BotFather)

### Environment Variables

The application requires several environment variables to function correctly. These can be defined in a `.env` file or directly in your environment:

- `BOT_TOKEN_1`: Token for the primary LinkFixer bot.
- `BOT_TOKEN_2`: Token for the secondary Chat Manager bot.
- `GOOGLE_API_KEY`: Your Google Gemini AI API key.
- `DATABASE_USERNAME`: Username for the H2 database (default: `sa`).
- `DATABASE_PASSWORD`: Password for the H2 database.
- `REDIS_HOST`: Redis server host (default: `redis`).
- `REDIS_PORT`: Redis server port (default: `6379`).

### Local Deployment with Docker Compose

1. Clone the repository.
2. Create a `.env` file with the required environment variables.
3. Start the services:
   ```bash
   docker compose up -d
   ```

## Development

### Building the project

The project uses Gradle. To build the JAR file locally:

```bash
./gradlew build -x test
```

### Testing

Run the test suite using:

```bash
./gradlew test
```

## Deployment

The project includes a GitHub Actions workflow (`deploy.yml`) that automates:
1. Building and pushing the Docker image to GitHub Container Registry (GHCR).
2. Deploying to a remote server using SSH and SCP.
3. Managing secrets and environment variables on the target host.

## Project Structure

- `src/main/kotlin/com/mamoru/LinkFixerBot.kt`: Main bot entry point.
- `src/main/kotlin/com/mamoru/service/`: Core business logic, including AI and message processing.
- `src/main/kotlin/com/mamoru/service/url/`: URL handling and processing pipeline.
- `src/main/resources/application.yml`: Application configuration and defaults.
- `docker-compose.yml`: Service orchestration for the application and Redis.
- `Dockerfile`: Multi-stage Docker build for the application environment.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
