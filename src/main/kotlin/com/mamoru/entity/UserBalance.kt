package com.mamoru.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_balance")
data class UserBalance(
    @Id
    val userId: Long,
    val starBalance: Int = 0
)
