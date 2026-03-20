package com.mamoru.service

import com.mamoru.repository.ChatSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TraitsScheduler(
    private val aiService: AiService,
    private val chatSettingsRepository: ChatSettingsRepository
) {
    private val logger = LoggerFactory.getLogger(TraitsScheduler::class.java)

    // Run every day at 03:00
    @Scheduled(cron = "0 0 3 * * *")
    fun dailyTraitsUpdate() {
        logger.info("Starting daily traits+interests update for all chats")
        val chatIds = chatSettingsRepository.findAll().map { it.chatId }
        for (chatId in chatIds) {
            try {
                aiService.updateAllTraitsForChat(chatId)
            } catch (e: Exception) {
                logger.error("Error updating traits for chat $chatId", e)
            }
        }
        logger.info("Daily traits+interests update completed")
    }
}
