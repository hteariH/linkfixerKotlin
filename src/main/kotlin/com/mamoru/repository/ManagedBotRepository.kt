package com.mamoru.repository

import com.mamoru.entity.ManagedBot
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ManagedBotRepository : MongoRepository<ManagedBot, String> {
    fun findByBotUsername(botUsername: String): ManagedBot?
}
