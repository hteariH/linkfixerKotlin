package com.mamoru.repository

import com.mamoru.entity.ChatSettings
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatRepository : MongoRepository<ChatSettings, Long> {
    fun findByChatId(chatId: Long): ChatSettings?
}

