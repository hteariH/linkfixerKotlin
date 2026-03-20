package com.mamoru

import com.mamoru.service.AiService
import com.mamoru.service.ChatSettingsService
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

class GrokBot(
    private val botToken: String,
    private val botName: String,
    private val aiService: AiService,
    private val chatSettingsService: ChatSettingsService
) : TelegramLongPollingBot(botToken) {

    private val logger = LoggerFactory.getLogger(GrokBot::class.java)

    override fun getBotUsername(): String = botName

    override fun onUpdateReceived(update: Update) {
        logger.info("Received update: {}", update)
        if (!update.hasMessage() || !update.message.hasText()) return
        val message = update.message
        val chatId = message.chatId
        val text = message.text ?: return
        val from = message.from ?: return
        val userId = from.id
        val username = from.userName ?: userId.toString()

        val isBotMentioned = text.contains("@$botName", ignoreCase = true)
        val isReplyToBot = message.isReply && message.replyToMessage?.from?.userName.equals(botName, ignoreCase = true)

        try {
            when {
                text.startsWith("/impersonate") -> handleImpersonate(chatId, text, message.messageId)
                text.startsWith("/setimpersonate") -> handleSetImpersonate(chatId, text, message.messageId)
                text.startsWith("/updatetraits") -> handleUpdateTraits(chatId, userId, username, message.messageId)
                text.startsWith("/showtraits") -> handleShowTraits(chatId, text, message.messageId)
                text.startsWith("/") -> { /* ignore unknown commands */ }
                isBotMentioned || isReplyToBot -> handleBotAddressed(chatId, userId, username, text, message.messageId)
                else -> handleRegularMessage(chatId, userId, username, text)
            }
        } catch (e: Exception) {
            logger.error("Error processing message in chat $chatId", e)
        }
    }

    private fun handleBotAddressed(chatId: Long, userId: Long, username: String, text: String, replyToMessageId: Int) {
        aiService.saveMessage(chatId, userId, username, text)

        val settings = chatSettingsService.getChatSettings(chatId)
        val targetUserId = settings.impersonateUserId ?: return
        val targetUsername = settings.impersonateUsername

        Thread {
            val response = aiService.impersonate(chatId, targetUserId, targetUsername, text)
            send(chatId, response, replyToMessageId)
        }.start()
    }

    private fun handleRegularMessage(chatId: Long, userId: Long, username: String, text: String) {
        aiService.saveMessage(chatId, userId, username, text)

        val settings = chatSettingsService.getChatSettings(chatId)
        val targetUserId = settings.impersonateUserId ?: return
        val targetUsername = settings.impersonateUsername

        // Load traits to check interests
//        val traits = aiService.findTraitsByUsername(chatId, targetUsername ?: "") ?: return

        // Respond if the impersonated user is @mentioned or their interests are referenced
//        Thread {
//            if (aiService.shouldRespond(text, targetUsername, traits.interests)) {
//                val response = aiService.impersonate(chatId, targetUserId, targetUsername, text)
//                send(chatId, response)
//            }
//        }.start()
    }

    private fun handleImpersonate(chatId: Long, text: String, replyToMessageId: Int) {
        val parts = text.removePrefix("/impersonate").trim().split(" ", limit = 2)
        val targetUsername = parts.getOrNull(0)?.removePrefix("@")
        val triggerText = parts.getOrNull(1) ?: "Что ты думаешь об этом?"

        if (targetUsername.isNullOrBlank()) {
            send(chatId, "Использование: /impersonate @username [вопрос]", replyToMessageId)
            return
        }

        send(chatId, "⏳ Генерирую ответ от имени $targetUsername...", replyToMessageId)
        Thread {
            val traits = aiService.findTraitsByUsername(chatId, targetUsername)
            if (traits == null) {
                send(chatId, "Не найдено данных для пользователя $targetUsername. Нужно больше сообщений.", replyToMessageId)
                return@Thread
            }
            val response = aiService.impersonate(chatId, traits.userId, traits.username ?: targetUsername, triggerText)
            send(chatId, response, replyToMessageId)
        }.start()
    }

    private fun handleSetImpersonate(chatId: Long, text: String, replyToMessageId: Int) {
        val targetUsername = text.removePrefix("/setimpersonate").trim().removePrefix("@")
        if (targetUsername.isBlank()) {
            send(chatId, "Использование: /setimpersonate @username", replyToMessageId)
            return
        }

        val traits = aiService.findTraitsByUsername(chatId, targetUsername)
        if (traits == null) {
            send(chatId, "Не найдено данных для пользователя $targetUsername. Сначала нужно накопить сообщения.", replyToMessageId)
            return
        }

        chatSettingsService.updateImpersonateUser(chatId, traits.userId, traits.username ?: targetUsername)
        send(chatId, "✅ Теперь бот будет отвечать от имени $targetUsername в этом чате.", replyToMessageId)
    }

    private fun handleShowTraits(chatId: Long, text: String, replyToMessageId: Int) {
        val targetUsername = text.removePrefix("/showtraits").trim().removePrefix("@")
        if (targetUsername.isBlank()) {
            send(chatId, "Использование: /showtraits @username", replyToMessageId)
            return
        }
        val traits = aiService.findTraitsByUsername(chatId, targetUsername)
        if (traits == null) {
            send(chatId, "Не найдено данных для пользователя $targetUsername.", replyToMessageId)
            return
        }
        val interestsPart = if (traits.interests.isNotBlank()) "\n\n🎯 Интересы:\n${traits.interests}" else ""
        val fullText = "👤 Характеристики $targetUsername:\n\n${traits.traits}$interestsPart"
        sendLong(chatId, fullText, replyToMessageId)
    }

    private fun sendLong(chatId: Long, text: String, replyToMessageId: Int? = null) {
        val limit = 4096
        if (text.length <= limit) {
            send(chatId, text, replyToMessageId)
            return
        }
        var start = 0
        var firstChunk = true
        while (start < text.length) {
            val end = minOf(start + limit, text.length)
            send(chatId, text.substring(start, end), if (firstChunk) replyToMessageId else null)
            firstChunk = false
            start = end
        }
    }

    private fun handleUpdateTraits(chatId: Long, userId: Long, username: String, replyToMessageId: Int) {
        Thread {
            aiService.updateAllTraitsForChat(chatId)
            send(chatId, "✅ Характеристики и интересы обновлены для всех пользователей", replyToMessageId)
        }.start()
    }

    private fun send(chatId: Long, text: String, replyToMessageId: Int? = null) {
        try {
            val msg = SendMessage()
            msg.chatId = chatId.toString()
            msg.text = text
            if (replyToMessageId != null) msg.replyToMessageId = replyToMessageId
            execute(msg)
        } catch (e: Exception) {
            logger.error("Failed to send message to chat $chatId", e)
        }
    }
}
