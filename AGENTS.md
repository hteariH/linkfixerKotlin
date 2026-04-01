# Project Agents

This document describes the key services and components (agents) that make up the **LinkFixerBot** system. Each component has a specific responsibility in processing Telegram updates, handling media, or integrating with AI.

## Core Agents

### 1. **LinkFixerBot**
- **File**: `src/main/kotlin/com/mamoru/LinkFixerBot.kt`
- **Role**: The main entry point for Telegram updates. It acts as the primary coordinator, receiving messages and delegating them to the appropriate handler services.
- **Responsibilities**:
    - Receiving updates via `onUpdateReceived`.
    - Routing text messages to `MessageProcessorService`.
    - Routing media/voice messages to `MediaHandlerService`.
    - Sending processed responses back to Telegram.

### 2. **MessageProcessorService**
- **File**: `src/main/kotlin/com/mamoru/service/MessageProcessorService.kt`
- **Role**: Handles text-based logic and determines how a message should be processed.
- **Responsibilities**:
    - Detecting bot mentions and replies.
    - Orchestrating the URL processing pipeline.
    - Interfacing with `GeminiAIService` for text generation.
    - Preparing administrative forwarding for certain messages.

### 3. **MediaHandlerService**
- **File**: `src/main/kotlin/com/mamoru/service/MediaHandlerService.kt`
- **Role**: Manages the downloading and processing of rich media.
- **Responsibilities**:
    - Handling TikTok and Instagram URLs by downloading videos via specialized downloader services.
    - Caching downloaded videos to avoid redundant processing.
    - Transcribing voice messages using AI.

### 4. **GeminiAIService**
- **File**: `src/main/kotlin/com/mamoru/service/GeminiAIService.kt`
- **Role**: Integrates the bot with Google's Gemini AI models.
- **Responsibilities**:
    - Generating responses for mentions and impersonations.
    - Transcribing voice-to-text.
    - Analyzing images sent in messages.
    - Performing Text-to-Speech (TTS) operations.

## Support Services

### 5. **TikTokDownloaderService & InstagramDownloaderService**
- **Files**: `src/main/kotlin/com/mamoru/service/TikTokDownloaderService.kt`, `src/main/kotlin/com/mamoru/service/InstagramDownloaderService.kt`
- **Role**: Low-level downloaders powered by `yt-dlp`.
- **Responsibilities**:
    - Extracting video IDs from social media URLs.
    - Executing `yt-dlp` commands to download video and audio streams.
    - Merging streams into a compatible MP4 format.

### 6. **VideoCacheService**
- **File**: `src/main/kotlin/com/mamoru/service/VideoCacheService.kt`
- **Role**: An in-memory cache for downloaded video paths.
- **Responsibilities**:
    - Tracking already downloaded videos to speed up response times for duplicate URLs.

### 7. **MessageAnalyzerService**
- **File**: `src/main/kotlin/com/mamoru/service/MessageAnalyzerService.kt`
- **Role**: Collects and analyzes user message history.
- **Responsibilities**:
    - Saving incoming messages from target users to local storage for future AI context (impersonation).

### 8. **ScheduledMessageService**
- **File**: `src/main/kotlin/com/mamoru/service/ScheduledMessageSenderService.kt`
- **Role**: Handles background tasks and periodic broadcasts.
- **Responsibilities**:
    - Sending daily countdown messages to victory.
    - Sending daily jokes to configured chats.
    - Performing scheduled cleanup of video caches and downloaded files.

### 9. **DownloadCleanupService**
- **File**: `src/main/kotlin/com/mamoru/service/DownloadCleanupService.kt`
- **Role**: Startup maintenance utility.
- **Responsibilities**:
    - Cleaning the download directory upon application startup to ensure a fresh state.

## Configuration & Management

### 10. **ChatSettingsManagementService**
- **File**: `src/main/kotlin/com/mamoru/service/ChatSettingsManagementService.kt`
- **Role**: Manages persistent configuration for different Telegram chats.
- **Responsibilities**:
    - Storing and retrieving chat-specific settings (e.g., whether to send daily jokes) from the H2 database.
