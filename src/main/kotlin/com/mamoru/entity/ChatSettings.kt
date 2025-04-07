package com.mamoru.entity

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

    val sendRandomJoke: Boolean = false
)
