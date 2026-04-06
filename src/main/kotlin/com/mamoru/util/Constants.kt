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
    }

    object Message {
        const val JOKE_ENABLED = "Joke enabled"
        const val JOKE_DISABLED = "Joke disabled"
        const val PICTURE_COMMENT_ENABLED = "Picture commenting enabled"
        const val PICTURE_COMMENT_DISABLED = "Picture commenting disabled"
        const val JOKE_PROMPT_UPDATED = "Joke prompt updated successfully!"
        const val JOKE_PROMPT_HELP = "Please provide a prompt after the command. Example: /setJokePrompt Tell me a joke about programming"
        const val PICTURE_PROMPT_UPDATED = "Picture comment prompt updated successfully!"
        const val PICTURE_PROMPT_HELP = "Please provide a prompt after the command. Example: /setPicturePrompt Comment on this picture as if you were a famous comedian"
    }

    object AI {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val BACKUP_MODEL = "gemini-2.5-flash-lite"
        val MODEL_CANDIDATES = listOf("gemini-flash-latest", DEFAULT_MODEL, BACKUP_MODEL, "gemini-2.0-flash")
        const val DEFAULT_JOKE_FAILURE_MESSAGE = "Вибач, я шутку не придумав"
        const val DEFAULT_PICTURE_FAILURE_MESSAGE = "Не можу прокоментувати це зображення"
        const val DEFAULT_JOKE_PROMPT = "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку(просто роскажи шутку/анекдот, не вітайся, не роби висновків, також знай що зараз 2025 рік і на виборах президента США переміг Дональд Трамп)"
        const val PICTURE_ANALYSIS_INSTRUCTION = "Уважно проаналізуй зображення та надай детальний коментар САМЕ про те, що ти бачиш на цьому конкретному зображенні. Опиши об'єкти, людей, дії, обстановку та інші деталі, які ти можеш розпізнати. Не давай загальних коментарів, які могли б підійти до будь-якого зображення. Твій коментар має чітко відображати унікальний зміст цього конкретного фото у образливому тоні."
    }
}
