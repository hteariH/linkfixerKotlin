package com.mamoru.service

import com.mamoru.HydraManagerBot
import com.mamoru.repository.ChatRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import com.mamoru.repository.UserCharacterRepository
import com.mamoru.entity.UserCharacter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Random

@Service
class ScheduledMessageService(
    private val bot: HydraManagerBot,
    private val chatRepository: ChatRepository,
    @Qualifier("geminiAIService") private val aiService: AIService,
    @Qualifier("groqAIService") private val groqAIService: AIService,
    private val userCharacterRepository: UserCharacterRepository,
    private val messageAnalyzerService: MessageAnalyzerService
) {
    private val logger = LoggerFactory.getLogger(ScheduledMessageService::class.java)
    private var lastRunDate: LocalDate? = null
    private var nextRunTime: LocalTime = generateRandomTime()
    private val random = Random()

    companion object {
        const val TARGET_CHAT_ID = -1002590623139L
    }

    private fun generateRandomTime(): LocalTime {
        val hour = Random().nextInt(24)
        val minute = Random().nextInt(60)
        return LocalTime.of(hour, minute)
    }

    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    fun checkAndRunDailyUpdate() {
        val today = LocalDate.now()
        val now = LocalTime.now()
        logger.info("Daily update check: lastRunDate=$lastRunDate, now=$now, nextRunTime=$nextRunTime")
        if (lastRunDate != today && now.isAfter(nextRunTime)) {
            updateCharacterDescriptionsAndTags()
            lastRunDate = today
            nextRunTime = generateRandomTime()
            logger.info("Daily update finished. Next run scheduled for a random time tomorrow (or later today if missed): $nextRunTime")
        }
    }

    fun updateCharacterDescriptionsAndTags() {
        logger.info("Starting daily character description update for chat $TARGET_CHAT_ID")
        val groq = groqAIService as GroqAIService
        
        val users = userCharacterRepository.findAll()
        if (users.isEmpty()) {
            logger.info("No users found in user_character collection to update.")
            return
        }

        val updatedUsers = mutableListOf<UserCharacter>()
        for (user in users) {
            val history = messageAnalyzerService.readSavedMessages(user.userId)
            if (!history.isNullOrBlank()) {
                val description = groq.generateCharacterDescription(history)
                if (description != "Не удалось составить описание.") {
                    updatedUsers.add(user.copy(characterDescription = description, lastUpdated = Instant.now()))
                    logger.info("Updated character description for userId=${user.userId}")
                }
            }
        }

        if (updatedUsers.isNotEmpty()) {
            userCharacterRepository.saveAll(updatedUsers)
            
            // Pick one random user from updated ones to change MemberTag
            val luckyUser = updatedUsers.random()
            val newTag = groq.generateMemberTag(luckyUser.characterDescription!!)
            if (newTag != "Участник") {
                val oldTag = luckyUser.memberTag ?: "отсутствует"
                bot.setMemberTag(TARGET_CHAT_ID, luckyUser.userId, newTag)
                userCharacterRepository.save(luckyUser.copy(memberTag = newTag, lastUpdated = Instant.now()))
                logger.info("Updated MemberTag for userId=${luckyUser.userId} to '$newTag'")
                
                val userName = luckyUser.lastKnownName ?: "Пользователь"
                val notification = "У пользователя $userName изменен тег: '$oldTag' ➡️ '$newTag'"
                bot.sendMessageToChat(TARGET_CHAT_ID, notification)
            }
        }

        logger.info("Completed daily character description update")
    }

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
