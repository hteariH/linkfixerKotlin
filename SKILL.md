# TelegramBot Skill: Tech, Monetization, and Deployment

This document outlines the core technical foundations of the **HydraManagerBot** ecosystem, focusing on the stack, the economy, and the infrastructure.

## 1. Tech Stack
- **Language**: Kotlin (JVM 25+)
- **Framework**: Spring Boot 4.x (Dependency Injection, Service-oriented architecture)
- **Telegram SDK**: `telegrambots-springboot-long-polling-starter` (v7+)
- **LLM Integration**: 
  - **Gemini AI**: Primary engine (via Google AI SDK).
  - **Groq**: OpenAI-compatible fallback for high-speed text generation.
- **Persistence**: 
  - **MongoDB**: Primary document store for user balances, chat settings, and bot configurations.
  - **Redis**: Fast caching layer for operational data.
- **Context Management**: 
  - **In-memory**: LRU-based message caching for reply chain reconstruction.
  - **File-based**: Historical message logs for long-term "personality" training.

## 2. Monetization (Telegram Stars)
The system implements a delegated monetization model using Telegram Stars:
- **Pre-Checkout Logic**: Centralized handling of `PreCheckoutQuery` and `SuccessfulPayment` updates to ensure balance updates are consistent.
- **Gated Processing**: Every AI interaction is intercepted by a "Balance Gate" service that checks for sufficient Stars before invoking the LLM.

## 3. Deployment
The application is designed for containerized environments with a focus on persistence and scalability.
- **Containerization**: Multi-stage `Dockerfile` (Eclipse Temurin 25) for optimized, small runtime images.
- **Orchestration**: `docker-compose` setup including the Kotlin application, MongoDB 7, and Redis Alpine.
- **Cloud Infrastructure**: 
  - **Persistence**: Mounted volumes (`/data`) for storing historical logs and local database files.
- **Environment Driven**: Configuration managed via environment variables (Tokens, API Keys, DB URIs).
