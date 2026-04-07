package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "managed_bots")
data class ManagedBot(
    @Id
    val id: String? = null,

    @Indexed(unique = true)
    val botUsername: String,

    val botId: Long,
    val targetUserId: Long
)
