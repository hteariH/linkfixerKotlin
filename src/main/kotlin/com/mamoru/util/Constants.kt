package com.mamoru.util

/**
 * Constants used throughout the application
 */
object Constants {
    /**
     * Chat and user related constants
     */
    object Chat {
        const val ADMIN_CHAT_ID = 123616664L
    }

    /**
     * URL types for processing
     */
    object UrlType {
        const val TIKTOK = "tiktok"
        const val INSTAGRAM = "instagram"
        const val TWITTER = "twitter"
    }

    /**
     * Bot command prefixes
     */
    object Command {
        const val TOGGLE_COUNTER = "/togglecounter"
        const val TOGGLE_JOKE = "/togglejoke"
        const val TOGGLE_PICTURE_COMMENT = "/togglepicturecomment"
        const val TOGGLE_AUDIO_TRANSCRIPTION = "/toggleaudiotranscription"
        const val GET_RANDOM_JOKE = "/getRandomJoke"
        const val SET_JOKE_PROMPT = "/setJokePrompt"
        const val SET_PICTURE_PROMPT = "/setPicturePrompt"
        const val TTS = "/tts"
    }

    /**
     * Response messages
     */
    object Message {
        const val COUNTER_ENABLED = "Counter until win will now be shown in this chat"
        const val COUNTER_DISABLED = "Counter until win will not be shown in this chat"
        const val JOKE_ENABLED = "Joke enabled"
        const val JOKE_DISABLED = "Joke disabled"
        const val PICTURE_COMMENT_ENABLED = "Picture commenting enabled"
        const val PICTURE_COMMENT_DISABLED = "Picture commenting disabled"
        const val AUDIO_TRANSCRIPTION_ENABLED = "Audio transcription enabled"
        const val AUDIO_TRANSCRIPTION_DISABLED = "Audio transcription disabled"
        const val JOKE_PROMPT_UPDATED = "Joke prompt updated successfully!"
        const val JOKE_PROMPT_HELP = "Please provide a prompt after the command. Example: /setJokePrompt Tell me a joke about programming"
        const val PICTURE_PROMPT_UPDATED = "Picture comment prompt updated successfully!"
        const val PICTURE_PROMPT_HELP = "Please provide a prompt after the command. Example: /setPicturePrompt Comment on this picture as if you were a famous comedian"
        const val TTS_HELP = "Please provide text after the command. Example: /tts Hello, this is a test message"
        const val TTS_FAILURE = "Failed to generate voice message"
    }

    /**
     * AI model related constants
     */
    object AI {
        const val DEFAULT_MODEL = "gemini-2.0-flash-001"
        const val TTS_MODEL = "gemini-2.5-flash-preview-tts"
        const val DEFAULT_JOKE_FAILURE_MESSAGE = "Вибач, я шутку не придумав"
        const val DEFAULT_PICTURE_FAILURE_MESSAGE = "Не можу прокоментувати це зображення"
        const val DEFAULT_AUDIO_FAILURE_MESSAGE = "Не можу розпізнати аудіо повідомлення"
        const val DEFAULT_JOKE_PROMPT = "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку(просто роскажи шутку/анекдот, не вітайся, не роби висновків, також знай що зараз 2025 рік і на виборах президента США переміг Дональд Трамп)"
        const val PICTURE_ANALYSIS_INSTRUCTION = "Уважно проаналізуй зображення та надай детальний коментар САМЕ про те, що ти бачиш на цьому конкретному зображенні. Опиши об'єкти, людей, дії, обстановку та інші деталі, які ти можеш розпізнати. Не давай загальних коментарів, які могли б підійти до будь-якого зображення. Твій коментар має чітко відображати унікальний зміст цього конкретного фото у схвальному тоні."
        const val AUDIO_TRANSCRIPTION_INSTRUCTION = "Расшифруй это аудиосообщение. Напиши только текст, который ты слышишь, без дополнительных комментариев."
    }
}
