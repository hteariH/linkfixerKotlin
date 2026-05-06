package com.mamoru.util

object Constants {

    object Command {
        const val TOGGLE_JOKE = "/togglejoke"
        const val TOGGLE_PICTURE_COMMENT = "/togglepicturecomment"
        const val GET_RANDOM_JOKE = "/getRandomJoke"
        const val SET_JOKE_PROMPT = "/setJokePrompt"
        const val SET_PICTURE_PROMPT = "/setPicturePrompt"
        const val CREATE_BOT = "/createBot"
        const val ACTIVATE_BOT = "/activateBot"
        const val SEND_INVOICE = "/sendInvoice"
        const val AGENT = "/agent"
        const val HELLO_WORLD = "/приветмир"
    }

    object Message {
        const val AGENT_DISPATCHED = "✅ Команда отправлена в GitHub Actions. Следи за прогрессом во вкладке Actions репозитория."
        const val AGENT_NO_BALANCE = "❌ Недостаточно звёзд для выполнения команды. Нужно 10 ⭐."
        const val AGENT_USAGE = "Использование: /agent [инструкция]\nПример: /agent Добавь unit-тест для класса MessageCacheService"
        const val JOKE_ENABLED = "Joke enabled"
        const val JOKE_DISABLED = "Joke disabled"
        const val PICTURE_COMMENT_ENABLED = "Picture commenting enabled"
        const val PICTURE_COMMENT_DISABLED = "Picture commenting disabled"
        const val JOKE_PROMPT_UPDATED = "Joke prompt updated successfully!"
        const val JOKE_PROMPT_HELP =
            "Please provide a prompt after the command. Example: /setJokePrompt Tell me a joke about programming"
        const val PICTURE_PROMPT_UPDATED = "Picture comment prompt updated successfully!"
        const val PICTURE_PROMPT_HELP =
            "Please provide a prompt after the command. Example: /setPicturePrompt Comment on this picture as if you were a famous comedian"
    }

    object AI {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val BACKUP_MODEL = "gemini-2.5-flash-lite"
        val MODEL_CANDIDATES =
            listOf("gemini-3-flash-preview",DEFAULT_MODEL, "gemini-3.1-flash-lite-preview", BACKUP_MODEL)

        val GROQ_MODEL_CANDIDATES = listOf(
            "groq/compound",
            "openai/gpt-oss-120b",
            "gemma2-9b-it"
        )
        val GROQ_VISION_MODEL_CANDIDATES = listOf(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-128e-instruct"
        )

        // Saved message history is capped to avoid exceeding Groq's token limits.
        // ~4 000 chars ≈ 1 500–2 000 tokens for Cyrillic text.
        const val GROQ_MAX_SAVED_MESSAGES_CHARS = 4_000

        // How many messages from the reply chain / recent history to include
        const val GROQ_MAX_REPLY_CHAIN_MESSAGES = 10
        const val GROQ_MAX_RECENT_MESSAGES = 15

        const val DEFAULT_JOKE_FAILURE_MESSAGE = "Вибач, я шутку не придумав"
        const val DEFAULT_PICTURE_FAILURE_MESSAGE = "Не можу прокоментувати це зображення"
        const val DEFAULT_JOKE_PROMPT =
            "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку(просто роскажи шутку/анекдот, не вітайся, не роби висновків, також знай що зараз 2025 рік і на виборах президента США переміг Дональд Трамп)"
        const val PICTURE_ANALYSIS_INSTRUCTION =
            "Уважно проаналізуй зображення та надай детальний коментар САМЕ про те, що ти бачиш на цьому конкретному зображенні. Опиши об'єкти, людей, дії, обстановку та інші деталі, які ти можеш розпізнати. Не давай загальних коментарів, які могли б підійти до будь-якого зображення. Твій коментар має чітко відображати унікальний зміст цього конкретного фото у образливому тоні."
    }
}
