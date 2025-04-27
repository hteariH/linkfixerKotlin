package com.mamoru.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "chat_settings")
data class ChatSettings(
    @Id
    val chatId: Long,

    // Default to true
    val sendCounterUntilWin: Boolean = false,

    val sendRandomJoke: Boolean = false,

    @Column(name = "COMMENTONPICTURES")
    val commentOnPictures: Boolean = false,

    @Column(name = "JOKEPROMPT", length = 1000)
    val jokePrompt: String = "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку(просто роскажи шутку/анекдот, не вітайся, не роби висновків, також знай що зараз 2025 рік і на виборах президента США переміг Дональд Трамп)",

    @Column(name = "PICTUREPROMPT", length = 1000)
    val picturePrompt: String = "Ти - Володимир Зеленьский. Не забувай, що ти президент воюючої країни, також твоє улюблене слово - потужно. при відповідях не вітайся і пиши максимально коротко, а також не забудь обматерити і змішати з гівном того хто відправив тобі повідомлення"
)
