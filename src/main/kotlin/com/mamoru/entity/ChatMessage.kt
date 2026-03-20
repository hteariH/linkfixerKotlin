package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "chat_messages")
data class ChatMessage(
    @Id
    val id: String? = null,
    val chatId: Long,
    val userId: Long,
    val username: String? = null,
    val text: String,
    val timestamp: Instant = Instant.now()
)
