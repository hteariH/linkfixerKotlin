# Project Agents: HydraManagerBot

This document describes the key services and components (agents) that make up the **HydraManagerBot** system. The bot is designed for AI-based impersonation and message processing in Telegram.

## Core Agents

### 1. **HydraManagerBot**
- **File**: `src/main/kotlin/com/mamoru/HydraManagerBot.kt`
- **Role**: The main Telegram bot implementation. It handles incoming updates and coordinates message processing.
- **Responsibilities**:
    - Receiving updates via `onUpdateReceived`.
    - Managing both primary and managed (impersonation) bot instances.
    - Delegating text messages to `MessageProcessorService`.
    - Routing commands to `CommandHandlerService`.
    - Caching messages for reply chain context.

### 2. **MessageProcessorService**
- **File**: `src/main/kotlin/com/mamoru/service/MessageProcessorService.kt`
- **Role**: Orchestrates the logic for processing text messages and generating AI responses.
- **Responsibilities**:
    - Determining if a message requires an AI response (mentions, replies, or impersonation).
    - Interfacing with `GeminiAIService` to generate contextual replies.
    - Handling "comment on pictures" logic.

### 3. **GeminiAIService**
- **File**: `src/main/kotlin/com/mamoru/service/GeminiAIService.kt`
- **Role**: Integrates with Google's Gemini AI model.
- **Responsibilities**:
    - Generating responses based on provided prompts and context.
    - Managing system instructions for impersonation and general assistance.
    - Analyzing image content when required.

### 4. **MessageAnalyzerService**
- **File**: `src/main/kotlin/com/mamoru/service/MessageAnalyzerService.kt`
- **Role**: Collects and analyzes user message history to build context for AI impersonation.
- **Responsibilities**:
    - Saving messages from target users to the database.
    - Providing historical context for "who am I" type queries.

## Support Services

### 5. **MessageCacheService**
- **File**: `src/main/kotlin/com/mamoru/service/MessageCacheService.kt`
- **Role**: In-memory and persistent cache for message history.
- **Responsibilities**:
    - Storing recent messages to reconstruct conversation reply chains.
    - Tracking sent messages to allow the bot to "remember" its own replies.

### 6. **ManagedBotService**
- **File**: `src/main/kotlin/com/mamoru/service/ManagedBotService.kt`
- **Role**: Manages additional "impersonation" bot instances.
- **Responsibilities**:
    - Initializing and starting secondary bot instances from the database.
    - Handling the lifecycle of dynamically added bots.

### 7. **CommandHandlerService**
- **File**: `src/main/kotlin/com/mamoru/service/CommandHandlerService.kt`
- **Role**: Processes administrative and user commands.
- **Responsibilities**:
    - Handling commands like `/start`, `/help`, and chat configuration commands.
    - Routing specific settings changes to `ChatSettingsManagementService`.

### 8. **ChatSettingsManagementService**
- **File**: `src/main/kotlin/com/mamoru/service/ChatSettingsManagementService.kt`
- **Role**: Manages persistent configuration for different Telegram chats.
- **Responsibilities**:
    - Storing and retrieving chat-specific settings (e.g., AI personality, active features) from MongoDB.

### 9. **ScheduledMessageSenderService**
- **File**: `src/main/kotlin/com/mamoru/service/ScheduledMessageSenderService.kt`
- **Role**: Handles background tasks and periodic actions.
- **Responsibilities**:
    - Performing scheduled cleanup or status updates (implementation specific).

## Data Access

### 10. **ManagedBotRepository & ChatRepository**
- **Files**: `src/main/kotlin/com/mamoru/repository/ManagedBotRepository.kt`, `src/main/kotlin/com/mamoru/repository/ChatRepository.kt`
- **Role**: MongoDB repositories for persistent storage.
- **Responsibilities**:
    - Storing bot configurations and chat settings.
