package com.mamoru.repository

import com.mamoru.entity.ChatMessage
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : MongoRepository<ChatMessage, String> {
    fun findTop10ByChatIdOrderByTimestampDesc(chatId: Long): List<ChatMessage>
    fun findTop50ByChatIdOrderByTimestampAsc(chatId: Long): List<ChatMessage>
    fun findTop100ByChatIdAndUserIdOrderByTimestampAsc(chatId: Long, userId: Long): List<ChatMessage>
    fun findAllByChatId(chatId: Long): List<ChatMessage>
    fun deleteByChatId(chatId: Long)
}
