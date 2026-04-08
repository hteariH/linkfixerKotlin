package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class BotRegistryService {
    private val logger = LoggerFactory.getLogger(BotRegistryService::class.java)

    // Set of lowercase bot usernames (without @)
    private val registeredBotUsernames = ConcurrentHashMap.newKeySet<String>()

    fun registerBot(username: String) {
        val cleanName = username.removePrefix("@").lowercase()
        registeredBotUsernames.add(cleanName)
        logger.info("Registered bot: @$cleanName")
    }

    fun isBot(username: String?): Boolean {
        if (username == null) return false
        val cleanName = username.removePrefix("@").lowercase()
        return registeredBotUsernames.contains(cleanName)
    }

    fun getAllBotUsernames(): Set<String> = registeredBotUsernames.toSet()
}
