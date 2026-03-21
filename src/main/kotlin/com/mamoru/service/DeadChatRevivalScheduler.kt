package com.mamoru.service

import com.mamoru.GrokBot
import com.mamoru.repository.ChatSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@Component
class DeadChatRevivalScheduler(
    private val aiService: AiService,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val grokBot: GrokBot
) {
    private val logger = LoggerFactory.getLogger(DeadChatRevivalScheduler::class.java)

    // Check every 30 minutes
    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    fun checkAndReviveDeadChats() {
//        val chatIds = chatSettingsRepository.findAll().map { if (it.enableRevival) (it.chatId) }
        val chatIds = chatSettingsRepository.findAllChatSettingsByEnableRevival(true).map({it.chatId})
        val now = Instant.now()
        // Random threshold between 3 and 5 hours
        val thresholdHours = Random.nextLong(3, 6)
        val threshold = now.minus(thresholdHours, ChronoUnit.HOURS)

        for (chatId in chatIds) {
            try {
                val lastMessageTime = aiService.getLastMessageTime(chatId) ?: continue
                if (lastMessageTime.isBefore(threshold)) {
                    reviveChat(chatId)
                }
            } catch (e: Exception) {
                logger.error("Error checking dead chat for chatId $chatId", e)
            }
        }
    }

    private fun reviveChat(chatId: Long) {
        val usersWithTraits = aiService.getUsersWithTraits(chatId)
        if (usersWithTraits.isEmpty()) {
            logger.info("No users with traits found for chat $chatId, skipping revival")
            return
        }
        val target = usersWithTraits.random()
        logger.info("Reviving dead chat $chatId by addressing user ${target.username}")
        val message = aiService.generateRevivalMessage(chatId, target.userId, target.username)
        grokBot.sendRevivalMessage(chatId, message)
    }
}
