package com.mamoru.repository

import com.mamoru.entity.UserTraits
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTraitsRepository : MongoRepository<UserTraits, String> {
    fun findByUserIdAndChatId(userId: Long, chatId: Long): UserTraits?
    fun findByChatId(chatId: Long): List<UserTraits>
}
