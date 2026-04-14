package com.mamoru.service

import com.mamoru.HydraManagerBot
import com.mamoru.repository.ChatRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ScheduledMessageService(
    private val bot: HydraManagerBot,
    private val chatRepository: ChatRepository,
    private val aiService: AIService
) {
    private val logger = LoggerFactory.getLogger(ScheduledMessageService::class.java)

    @Scheduled(cron = "0 15 */7 * * *")
    fun sendDailyJokeMessage() {
        logger.info("Starting scheduled daily joke message sending")
        val chatsP = chatRepository.findAll()
        if (chatsP.isEmpty()) {
            logger.info("No active chats found to send the scheduled message")
            return
        }

        for (chat in chatsP) {
            if (chat.sendRandomJoke) {
                bot.sendMessageToChat(chat.chatId, aiService.getRandomJoke(chat.chatId))
                logger.info("Sent scheduled message with joke to chat ${chat.chatId} using bot ${bot.botName}")
            }
        }

        logger.info("Completed scheduled daily message sending")
    }
}
