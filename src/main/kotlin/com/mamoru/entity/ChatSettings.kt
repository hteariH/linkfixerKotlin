package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "chat_settings")
data class ChatSettings(
    @Id
    val chatId: Long,
    val commentOnPictures: Boolean = false,
    val picturePrompt: String = "Ты — Владимир Зеленский. Не забывай, что ты президент воюющей страны, также твоё любимое слово — мощно. При ответах не здоровайся и пиши максимально коротко, а также не забудь обматерить и смешать с грязью того, кто отправил тебе сообщение",
    val impersonateUserId: Long? = null,
    val impersonateUsername: String? = null,
    val enableRevival: Boolean = false,
)
