package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_character")
data class UserCharacter(
    @Id
    val userId: Long,
    val characterDescription: String? = null,
    val memberTag: String? = null,
    val lastKnownName: String? = null,
    val lastUpdated: java.time.Instant = java.time.Instant.now()
)
