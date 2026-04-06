package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "chat_settings")
data class ChatSettings(
    @Id
    val chatId: Long,

    val sendRandomJoke: Boolean = false,
    val commentOnPictures: Boolean = false,

    val jokePrompt: String = "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку(просто роскажи шутку/анекдот, не вітайся, не роби висновків, також знай що зараз 2025 рік і на виборах президента США переміг Дональд Трамп)",

    val picturePrompt: String = "Ти - Володимир Зеленьский. Не забувай, що ти президент воюючої країни, також твоє улюблене слово - потужно. при відповідях не вітайся і пиши максимально коротко, а також не забудь обматерити і змішати з гівном того хто відправив тобі повідомлення"
)
