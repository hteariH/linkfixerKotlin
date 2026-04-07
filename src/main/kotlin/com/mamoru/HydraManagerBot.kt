package com.mamoru

import com.mamoru.service.*
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.slf4j.LoggerFactory

open class HydraManagerBot(
    private val botToken: String,
    private val botName: String,
    private val commandHandlerService: CommandHandlerService,
    private val messageProcessorService: MessageProcessorService,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val messageAnalyzerService: MessageAnalyzerService,
    private val messageCacheService: MessageCacheService,
    // Non-null for managed bots: always impersonates this user when mentioned
    private val targetUserId: Long? = null,
    botOptions: DefaultBotOptions = DefaultBotOptions()
) : TelegramLongPollingBot(botOptions, botToken) {

    private val logger = LoggerFactory.getLogger(HydraManagerBot::class.java)

    override fun getBotUsername(): String = botName

    override fun onUpdateReceived(update: Update) {
        logger.debug("Received update: {}", update)
        if (!update.hasMessage()) return
        val message = update.message
        val chatId = message.chatId

        try {
            messageCacheService.cache(message)
            if (targetUserId == null) {
                messageAnalyzerService.analyzeMessageIfFromTargetUser(message)
            }
            val hasContent = message.hasText() || (targetUserId != null && message.caption != null)
            if (targetUserId != null) {
                logger.debug("[{}] Update received: chat={} msgId={} from=@{} hasText={} hasCaption={} replyToId={}",
                    botName, chatId, message.messageId,
                    message.from?.userName,
                    message.hasText(), message.caption != null,
                    message.replyToMessage?.messageId)
            }
            if (!hasContent) {
                if (targetUserId != null) logger.debug("[{}] Skipping — no text/caption", botName)
                return
            }

            if (targetUserId == null) {
                val commandResult = commandHandlerService.handleCommand(message)
                if (commandResult.isCommand) {
                    commandResult.responseText?.let { sendMessageToChat(chatId, it) }
                    return
                }
            }

            processTextMessage(message)

        } catch (e: Exception) {
            logger.error("Error processing update: ${e.message}", e)
        }
    }

    private fun processTextMessage(message: Message) {
        val replyChain = if (targetUserId != null)
            messageCacheService.getReplyChain(message.chatId, message.messageId)
        else emptyList()

        val recentMessages = if (targetUserId != null) {
            val excludeIds = (replyChain.map { it.messageId } + message.messageId).toSet()
            messageCacheService.getRecentMessages(message.chatId, limit = 30, excludeIds = excludeIds)
        } else emptyList()

        if (targetUserId != null) {
            logger.debug("[{}] Processing message, replyChain={} msgs, recentContext={} msgs",
                botName, replyChain.size, recentMessages.size)
        }

        val result = messageProcessorService.processTextMessage(
            message, this, botToken, botName, targetUserId, replyChain, recentMessages
        )

        val settings = chatSettingsManagementService.getChatSettings(message.chatId)
        val isManaged = targetUserId != null

        if (isManaged || settings.commentOnPictures) {
            result.mentionResponse?.let { responseText ->
                val parts = splitAndTruncate(responseText)
                parts.forEachIndexed { index, part ->
                    val sendMessage = SendMessage()
                    sendMessage.chatId = message.chatId.toString()
                    sendMessage.text = part
                    if (index == 0) sendMessage.replyToMessageId = message.messageId
                    try {
                        val sent = execute(sendMessage)
                        if (isManaged) {
                            messageCacheService.cacheSentMessage(
                                chatId = message.chatId, messageId = sent.messageId, text = part,
                                botUsername = botName,
                                replyToMessageId = if (index == 0) message.messageId else null
                            )
                            logger.debug("[{}] Cached own sent message id={}", botName, sent.messageId)
                        }
                        logger.info("Sent response to chat ${message.chatId}")
                    } catch (e: TelegramApiException) {
                        logger.error("Failed to send mention response: ${e.message}", e)
                    }
                }
            }
        }

        if (settings.commentOnPictures) {
            result.jokeResponse?.let { jokeText ->
                sendMessageToChat(message.chatId, jokeText)
            }
        }
    }

    private fun splitAndTruncate(text: String): List<String> {
        val separator = "----------------------------------------"
        val telegramMaxLen = 4096
        return text
            .split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part -> if (part.length > telegramMaxLen) part.substring(0, telegramMaxLen) else part }
    }

    fun sendMessageToChat(chatId: Long, text: String) {
        val parts = splitAndTruncate(text)
        if (parts.isEmpty()) return
        for (part in parts) {
            val message = SendMessage()
            message.chatId = chatId.toString()
            message.text = part
            try {
                val sent = execute(message)
                logger.info("Sent message ${sent.messageId} to chat $chatId")
            } catch (e: Exception) {
                logger.error("Failed to send message to chat $chatId: ${e.message}", e)
            }
        }
    }
}
