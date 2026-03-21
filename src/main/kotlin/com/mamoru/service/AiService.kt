package com.mamoru.service

import com.mamoru.entity.ChatMessage
import com.mamoru.entity.UserTraits
import com.mamoru.repository.ChatMessageRepository
import com.mamoru.repository.UserTraitsRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
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
     * Respond as a helpful assistant, including the sender's traits in context if available.
     */
    fun respondAsAssistant(chatId: Long, userId: Long, username: String?, triggerText: String): String {
        val traits = userTraitsRepository.findByUserIdAndChatId(userId, chatId)
        val recentMessages = chatMessageRepository.findTop10ByChatIdOrderByTimestampDesc(chatId).reversed()

        val recallAll = triggerText.contains("вспомни всё", ignoreCase = true) ||
            triggerText.contains("вспомни все", ignoreCase = true) ||
            triggerText.contains("recall everything", ignoreCase = true)
        val allTraits = if (recallAll) userTraitsRepository.findByChatId(chatId) else emptyList()

        val systemPrompt = buildAssistantSystemPrompt(username, traits, allTraits)

        val historyMessages = recentMessages.map { msg ->
            val name = msg.username ?: "user_${msg.userId}"
            UserMessage("$name: ${msg.text}")
        }

        val messages = mutableListOf<Message>(SystemMessage(systemPrompt))
        messages.addAll(historyMessages)
        messages.add(UserMessage(triggerText))

        return try {
            chatClient.prompt().messages(messages).call().content() ?: "..."
        } catch (e: Exception) {
            logger.error("Error generating assistant response for user $userId in chat $chatId", e)
            "Ошибка при генерации ответа."
        }
    }

    /**
     * Generate a dead chat revival message targeting a specific user based on their interests.
     */
    fun generateRevivalMessage(chatId: Long, targetUserId: Long, targetUsername: String?): String {
        val traits = userTraitsRepository.findByUserIdAndChatId(targetUserId, chatId)
        val name = targetUsername?.let { "@$it" } ?: "участник чата"

        val prompt = buildRevivalPrompt(name, traits)

        return try {
            chatClient.prompt().user(prompt).call().content()
                ?: "Эй, $name, как дела? Давно не было активности в чате 👋"
        } catch (e: Exception) {
            logger.error("Error generating revival message for chat $chatId", e)
            "Эй, $name, как дела? Давно не было активности в чате 👋"
        }
    }

    fun findTraitsByUsername(chatId: Long, username: String): UserTraits? {
        return userTraitsRepository.findByChatId(chatId)
            .firstOrNull { it.username.equals(username, ignoreCase = true) }
    }

    fun getLastMessageTime(chatId: Long): Instant? {
        return chatMessageRepository.findTop1ByChatIdOrderByTimestampDesc(chatId).firstOrNull()?.timestamp
    }

    fun getUsersWithTraits(chatId: Long): List<UserTraits> {
        return userTraitsRepository.findByChatId(chatId)
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
            Thread.sleep(1000 * 60)
        }

        // Clear all messages except the last 10 (kept for context)
        val allMessages = chatMessageRepository.findAllByChatId(chatId)
        val toDelete = allMessages.sortedBy { it.timestamp }.dropLast(10)
        chatMessageRepository.deleteAll(toDelete)
        logger.info("Updated traits for all users in chat $chatId and cleared messages (kept last 10)")
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

    private fun buildAssistantSystemPrompt(username: String?, traits: UserTraits?, allUsersTraits: List<UserTraits> = emptyList()): String {
        val userPart = if (traits != null) {
            val name = username ?: "этот пользователь"
            val interestsPart = if (traits.interests.isNotBlank()) "\n\nИнтересы пользователя:\n${traits.interests}" else ""
            """
                
                Информация о пользователе, с которым ты общаешься ($name):
                ${traits.traits}$interestsPart
                
                Учитывай эту информацию при ответе, чтобы быть максимально полезным и релевантным.
            """.trimIndent()
        } else ""

        val allUsersPart = if (allUsersTraits.isNotEmpty()) {
            val usersInfo = allUsersTraits.joinToString("\n\n") { u ->
                val name = u.username ?: "user_${u.userId}"
                val interestsPart = if (u.interests.isNotBlank()) "\n  Интересы: ${u.interests}" else ""
                "👤 $name:\n  ${u.traits}$interestsPart"
            }
            "\n\nИнформация обо всех участниках чата:\n$usersInfo"
        } else ""

        return """
            Ты — полезный ассистент в Telegram-чате. Отвечай кратко, по делу и дружелюбно.
            Не притворяйся кем-то другим. Ты просто умный помощник.
            Отвечай на том языке, на котором написано сообщение.$userPart$allUsersPart
        """.trimIndent()
    }

    private fun buildRevivalPrompt(targetName: String, traits: UserTraits?): String {
        val interestsPart = if (traits != null && traits.interests.isNotBlank()) {
            "\n\nИнтересы пользователя:\n${traits.interests}"
        } else ""

        return """
            В чате давно не было сообщений. Придумай короткое, живое сообщение, чтобы оживить чат.
            Обратись к пользователю $targetName и задай ему интересный вопрос или предложи тему для обсуждения.$interestsPart
            
            Сообщение должно быть неформальным, коротким (1-2 предложения) и побуждать к ответу.
            Не объясняй, что чат был неактивен. Просто начни разговор естественно.
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
            
            Ответ дай в виде структурированного текста.
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
