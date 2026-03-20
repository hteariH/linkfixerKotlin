package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "user_traits")
data class UserTraits(
    @Id
    val id: String? = null,
    val userId: Long,
    val chatId: Long,
    val username: String? = null,
    val traits: String,
    val interests: String = "",
    val updatedAt: Instant = Instant.now()
)
