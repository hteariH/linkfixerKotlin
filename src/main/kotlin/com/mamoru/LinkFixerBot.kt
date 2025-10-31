package com.mamoru

import com.mamoru.service.*
import com.mamoru.service.url.ProcessedText
import com.mamoru.util.Constants
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.slf4j.LoggerFactory

/**
 * Main Telegram bot class that handles incoming updates and delegates processing to specialized services
 */
class LinkFixerBot(
    private val botToken: String,
    private val botName: String,
    private val commandHandlerService: CommandHandlerService,
    private val mediaHandlerService: MediaHandlerService,
    private val messageProcessorService: MessageProcessorService,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val messageAnalyzerService: MessageAnalyzerService
) : TelegramLongPollingBot(botToken) {

    private val logger = LoggerFactory.getLogger(LinkFixerBot::class.java)

    // Admin chat ID for forwarding messages
    override fun getBotUsername(): String {
        return botName
    }

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage()) return
        val message = update.message
        val chatId = message.chatId

        try {
            // Analyze message if it's from the target user (this happens regardless of other processing)
            messageAnalyzerService.analyzeMessageIfFromTargetUser(message)


            // Handle audio messages if the feature is enabled
            if (!botUsername.contains("HWSlavaBot", ignoreCase = true) && message.hasVoice() && chatSettingsManagementService.getChatSettings(chatId).transcribeAudio) {
                handleAudio(message)
                return
            }

            if (!message.hasText()) return

            // Handle commands
            val commandResult = commandHandlerService.handleCommand(message)
            if (commandResult.isCommand) {
                commandResult.responseText?.let { responseText ->
                    sendMessageToChat(chatId, responseText)
                }
                return
            }

            // Process regular text messages
            processTextMessage(message)

        } catch (e: Exception) {
            logger.error("Error processing update: ${e.message}", e)
        }
    }

    /**
     * Process a regular text message
     */
    private fun processTextMessage(message: Message) {
        val result = messageProcessorService.processTextMessage(message, this, botToken, botName)

        result.adminForwardMessage?.let { adminMessage ->
            try {
                execute(adminMessage)
            } catch (e: TelegramApiException) {
                logger.error("Failed to forward message to admin: ${e.message}", e)
            }
        }

        if (botUsername.contains("HWSlavaBot",ignoreCase = true)){
            logger.info("Skipping processing of message in HWSlavaBot")
            return
        }

        // Send mention response if generated (when bot is mentioned or replied to)
    if (chatSettingsManagementService.getChatSettings(message.chatId).commentOnPictures) {
        result.mentionResponse?.let { responseText ->
            val sendMessage = SendMessage()
            sendMessage.chatId = message.chatId.toString()
            sendMessage.text = responseText
            sendMessage.replyToMessageId = message.messageId
            try {
                execute(sendMessage)
                logger.info("Sent response to mention/reply in chat: ${message.chatId}")
            } catch (e: TelegramApiException) {
                logger.error("Failed to send mention response: ${e.message}", e)
            }
        }

        // Send joke response if generated
        result.jokeResponse?.let { jokeText ->
            sendMessageToChat(message.chatId, jokeText)
        }
    }

        // Handle processed URLs
        result.processedText?.let { processedText ->
            handleProcessedUrls(message, processedText)
        }
    }

    /**
     * Handle an audio message
     */
    private fun handleAudio(message: Message) {
        try {
            val sendMessage = mediaHandlerService.handleAudio(message, this, botToken)
            execute(sendMessage)
            logger.info("Sent audio transcription to chat: ${message.chatId}")
        } catch (e: Exception) {
            logger.error("Failed to handle audio: ${e.message}", e)
        }
    }

    /**
     * Handle processed URLs in a message
     */
    private fun handleProcessedUrls(message: Message, processedText: ProcessedText) {
        val processedUrls = processedText.processedUrls
        for (processedUrl in processedUrls) {
            when (processedUrl.type) {
                Constants.UrlType.TIKTOK -> {
                    val sendVideo = mediaHandlerService.handleTikTokUrl(message, processedUrl.original)
                    if (sendVideo != null) {
                        try {
                            execute(sendVideo)
                            logger.info("Sent TikTok video to chat: ${message.chatId}")
                        } catch (e: TelegramApiException) {
                            logger.error("Failed to send TikTok video: ${e.message}", e)
                            sendMessageToChat(message.chatId, "Failed to send TikTok video: ${e.message}")
                        }
                    }
                }
                Constants.UrlType.INSTAGRAM -> {
                    val sendVideo = mediaHandlerService.handleInstagramUrl(message, processedUrl.original)
                    if (sendVideo != null) {
                        try {
                            execute(sendVideo)
                            logger.info("Sent Instagram video to chat: ${message.chatId}")
                        } catch (e: TelegramApiException) {
                            logger.error("Failed to send Instagram video: ${e.message}", e)
                            sendMessageToChat(message.chatId, "Failed to send Instagram video: ${e.message}")
                        }
                    }
                }
                Constants.UrlType.TWITTER -> {
                    if (processedUrl.original != processedUrl.converted) {
                        sendMessageToChat(message.chatId, processedText.modifiedText)
                        return
                    }
                }
            }
        }
    }

    /**
     * Send a text message to a chat
     */
    fun sendMessageToChat(chatId: Long, text: String) {
        val message = SendMessage()
        message.chatId = chatId.toString()
        message.text = text
        try {
            execute(message)
            logger.info("Sent message to chat: $chatId")
        } catch (e: Exception) {
            logger.error("Failed to send message to chat $chatId: ${e.message}", e)
        }
    }
}
