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
                text.startsWith("/updatetraits") -> handleUpdateTraits(chatId, message.messageId)
                text.startsWith("/showtraits") -> handleShowTraits(chatId, text, message.messageId)
                text.startsWith("/revival") -> handleRevival(chatId, text, message.messageId)
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
        Thread {
            val response = aiService.respondAsAssistant(chatId, userId, username, text)
            send(chatId, response, replyToMessageId)
        }.start()
    }

    private fun handleRegularMessage(chatId: Long, userId: Long, username: String, text: String) {
        aiService.saveMessage(chatId, userId, username, text)
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

    private fun handleRevival(chatId: Long, text: String, replyToMessageId: Int) {
        val arg = text.removePrefix("/revival").trim().lowercase()
        when (arg) {
            "on", "enable" -> {
                chatSettingsService.updateEnableRevival(chatId, true)
                send(chatId, "✅ Dead chat revival enabled for this chat.", replyToMessageId)
            }
            "off", "disable" -> {
                chatSettingsService.updateEnableRevival(chatId, false)
                send(chatId, "🔕 Dead chat revival disabled for this chat.", replyToMessageId)
            }
            else -> {
                val current = chatSettingsService.getChatSettings(chatId).enableRevival
                send(chatId, "Dead chat revival is currently ${if (current) "✅ enabled" else "🔕 disabled"}. Use /revival on or /revival off.", replyToMessageId)
            }
        }
    }

    private fun handleUpdateTraits(chatId: Long, replyToMessageId: Int) {
        Thread {
            aiService.updateAllTraitsForChat(chatId)
            send(chatId, "✅ Характеристики и интересы обновлены для всех пользователей", replyToMessageId)
        }.start()
    }

    fun sendRevivalMessage(chatId: Long, text: String) {
        send(chatId, text)
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
