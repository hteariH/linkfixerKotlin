package com.mamoru.service

import com.mamoru.entity.ChatMessage
import com.mamoru.entity.UserTraits
import com.mamoru.repository.ChatMessageRepository
import com.mamoru.repository.UserTraitsRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AiService(
    private val chatClient: ChatClient,
    private val chatMessageRepository: ChatMessageRepository,
    private val userTraitsRepository: UserTraitsRepository
) {
    private val logger = LoggerFactory.getLogger(AiService::class.java)

    fun saveMessage(chatId: Long, userId: Long, username: String?, text: String) {
        chatMessageRepository.save(
            ChatMessage(chatId = chatId, userId = userId, username = username, text = text)
        )
    }

    /**
     * Returns true if the message text mentions the impersonated user by @username
     * or references any of their interests.
     */
    fun shouldRespond(text: String, targetUsername: String?, interests: String): Boolean {
        if (targetUsername != null && text.contains("@$targetUsername", ignoreCase = true)) return true
        if (interests.isBlank()) return false
        // Ask LLM if the message is related to any of the user's interests
        return try {
            val prompt = """
                Список интересов пользователя:
                $interests
                
                Сообщение из чата:
                "$text"
                
                Упоминается ли в сообщении хотя бы один из интересов пользователя? Ответь только "да" или "нет".
            """.trimIndent()
            val answer = chatClient.prompt().user(prompt).call().content() ?: "нет"
            answer.trim().lowercase().startsWith("да")
        } catch (e: Exception) {
            logger.error("Error checking interests relevance", e)
            false
        }
    }

    fun impersonate(chatId: Long, targetUserId: Long, targetUsername: String?, triggerText: String): String {
        val traits = userTraitsRepository.findByUserIdAndChatId(targetUserId, chatId)
            ?: return "Я не знаю этого пользователя. Сначала нужно накопить сообщения."

        val recentMessages = chatMessageRepository.findTop10ByChatIdOrderByTimestampDesc(chatId).reversed()
        val systemPrompt = buildImpersonationSystemPrompt(targetUsername, traits.traits, traits.interests)

        val historyMessages = recentMessages.map { msg ->
            val name = msg.username ?: "user_${msg.userId}"
            if (msg.userId == targetUserId) AssistantMessage("$name: ${msg.text}")
            else UserMessage("$name: ${msg.text}")
        }

        val messages = mutableListOf<Message>(SystemMessage(systemPrompt))
        messages.addAll(historyMessages)
        messages.add(UserMessage(triggerText))

        return try {
            chatClient.prompt().messages(messages).call().content() ?: "..."
        } catch (e: Exception) {
            logger.error("Error during impersonation for user $targetUserId in chat $chatId", e)
            "Ошибка при генерации ответа."
        }
    }

    fun findTraitsByUsername(chatId: Long, username: String): UserTraits? {
        return userTraitsRepository.findByChatId(chatId)
            .firstOrNull { it.username.equals(username, ignoreCase = true) }
    }

    /**
     * Extract and update traits + interests for all users in a chat based on their messages.
     * Called by the daily scheduler.
     */
    fun updateAllTraitsForChat(chatId: Long) {
        val messages = chatMessageRepository.findAllByChatId(chatId)
        if (messages.isEmpty()) return

        val byUser = messages.groupBy { it.userId }
        for ((userId, userMessages) in byUser) {
            if (userMessages.size < 3) continue
            val username = userMessages.last().username
            updateUserTraitsAndInterests(chatId, userId, username, userMessages)
            Thread.sleep(1000*60)
        }

        // Clear all messages except the last 10 (kept for impersonation context)
        val allMessages = chatMessageRepository.findAllByChatId(chatId)
        val toDelete = allMessages.sortedBy { it.timestamp }.dropLast(10)
        chatMessageRepository.deleteAll(toDelete)
        logger.info("Updated traits for all users in chat $chatId and cleared messages (kept last 10)")
    }

    fun updateUserTraits(chatId: Long, userId: Long, username: String?) {
        val messages = chatMessageRepository.findTop100ByChatIdAndUserIdOrderByTimestampAsc(chatId, userId)
        if (messages.size < 5) return
        updateUserTraitsAndInterests(chatId, userId, username, messages)
    }

    private fun updateUserTraitsAndInterests(
        chatId: Long, userId: Long, username: String?, messages: List<ChatMessage>
    ) {
        val messagesText = messages.joinToString("\n") { it.text }
        val existing = userTraitsRepository.findByUserIdAndChatId(userId, chatId)

        val traitsPrompt = buildTraitsExtractionPrompt(username, messagesText, existing?.traits)
        val interestsPrompt = buildInterestsExtractionPrompt(username, messagesText, existing?.interests)

        val traits = try {
            chatClient.prompt().user(traitsPrompt).call().content() ?: return
        } catch (e: Exception) {
            logger.error("Error extracting traits for user $userId in chat $chatId", e); return
        }

        val interests = try {
            chatClient.prompt().user(interestsPrompt).call().content() ?: ""
        } catch (e: Exception) {
            logger.error("Error extracting interests for user $userId in chat $chatId", e); ""
        }

        val updated = (existing ?: UserTraits(
            userId = userId, chatId = chatId, username = username, traits = traits, interests = interests
        )).copy(traits = traits, interests = interests, updatedAt = Instant.now())

        userTraitsRepository.save(updated)
        logger.info("Updated traits+interests for user $userId in chat $chatId")
    }

    private fun buildImpersonationSystemPrompt(username: String?, traits: String, interests: String): String {
        val name = username ?: "этот пользователь"
        val interestsPart = if (interests.isNotBlank()) "\n\nИнтересы и увлечения:\n$interests" else ""
        return """
            Ты полностью перевоплощаешься в реального человека по имени $name из Telegram-чата.
            
            Вот характеристики этого человека на основе его сообщений:
            $traits$interestsPart
            
            Правила:
            - Отвечай ТОЛЬКО от имени $name, полностью копируя его стиль, манеру речи, словарный запас
            - Используй те же слова-паразиты, сокращения, эмодзи если они есть
            - Не выходи из роли ни при каких обстоятельствах
            - Не упоминай что ты AI
            - Отвечай коротко и естественно, как в чате
        """.trimIndent()
    }

    private fun buildTraitsExtractionPrompt(username: String?, messages: String, existingTraits: String?): String {
        val name = username ?: "этот пользователь"
        val existingPart = if (existingTraits != null) "\n\nПредыдущие характеристики (обновить/дополнить):\n$existingTraits" else ""
        return """
            Проанализируй сообщения пользователя $name и составь детальный психологический и речевой портрет.
            
            Сообщения:
            $messages
            $existingPart
            
            Опиши:
            - Стиль речи (формальный/неформальный, мат, сленг, эмодзи)
            - Характерные фразы и слова-паразиты
            - Характер и поведение в чате (агрессивный, шутливый, серьёзный и т.д.)
            - Язык общения
            - Любые другие заметные особенности
            
            Ответ дай в виде структурированного текста для дальнейшего использования при имитации этого человека.
        """.trimIndent()
    }

    private fun buildInterestsExtractionPrompt(username: String?, messages: String, existingInterests: String?): String {
        val name = username ?: "этот пользователь"
        val existingPart = if (!existingInterests.isNullOrBlank()) "\n\nПредыдущие интересы (обновить/дополнить):\n$existingInterests" else ""
        return """
            Проанализируй сообщения пользователя $name и выяви его интересы, увлечения и любимые темы.
            
            Сообщения:
            $messages
            $existingPart
            
            Составь список интересов и увлечений пользователя: темы, о которых он часто говорит, хобби, любимые игры/фильмы/музыка/спорт и т.д.
            Перечисли кратко, каждый интерес на новой строке.
        """.trimIndent()
    }
}
