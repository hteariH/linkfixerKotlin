package com.mamoru.service

import com.mamoru.service.url.ProcessedText
import com.mamoru.service.url.UrlProcessingPipeline
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import kotlin.random.Random

/**
 * Service for processing Telegram messages
 */
@Service
class MessageProcessorService(
    private val urlProcessingPipeline: UrlProcessingPipeline,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val geminiAIService: GeminiAIService
) {
    private val logger = LoggerFactory.getLogger(MessageProcessorService::class.java)

    // Admin chat ID for forwarding messages
    private val ADMIN_CHAT_ID = 123616664L

    /**
     * Process a text message and handle URLs
     *
     * @param message The Telegram message to process
     * @return ProcessingResult containing the results of processing
     */
    fun processTextMessage(message: Message): ProcessingResult {
        val text = message.text
        val chatId = message.chatId
        val result = ProcessingResult()

        // Check for Zelensky mentions and possibly send a joke
        if (containsZelenskyMention(text) && 
            chatSettingsManagementService.getChatSettings(chatId).sendRandomJoke && 
            Random.nextBoolean()) {
            result.jokeResponse = geminiAIService.getRandomJoke(chatId)
            logger.info("Generated joke response for Zelensky mention in chat $chatId")
        }

        // Process URLs in the message
        val processedText = urlProcessingPipeline.processTextAndReplace(text)
        if (processedText.processedUrls.isNotEmpty()) {
            result.processedText = processedText
            logger.info("Processed ${processedText.processedUrls.size} URLs in message from chat $chatId")
        }

        // Forward message to admin
        result.adminForwardMessage = createAdminForwardMessage(message)

        // Register the chat
        chatSettingsManagementService.addChat(chatId)

        return result
    }

    /**
     * Handle a reply to a forwarded message (admin functionality)
     *
     * @param replyMessage The reply message from admin
     * @return ReplyResult containing the response message and confirmation message
     */
    fun handleReplyToForwardedMessage(replyMessage: Message): ReplyResult {
        val originalForwardedText: String = replyMessage.replyToMessage.text ?: ""
        val split = originalForwardedText.split(":")

        try {
            // Extract information from the forwarded message
            val originalChatId: Long = split.first().trim().toLong()
            val originalMessageId: Int = split.last().trim().toInt()
            val replyText: String = replyMessage.text ?: ""

            // Create reply message
            val sendMessage = SendMessage()
            sendMessage.setChatId(originalChatId)
            sendMessage.text = replyText
            sendMessage.replyToMessageId = originalMessageId

            // Create confirmation message
            val confirmMessage = SendMessage()
            confirmMessage.setChatId(replyMessage.chatId)
            confirmMessage.text = "Reply sent to chat $originalChatId"

            logger.info("Admin reply to chat $originalChatId processed")
            return ReplyResult(sendMessage, confirmMessage)
        } catch (e: NumberFormatException) {
            logger.error("Failed to parse chat ID or message ID: ${e.message}", e)
            val errorMessage = SendMessage()
            errorMessage.setChatId(replyMessage.chatId)
            errorMessage.text = "Failed to send reply: ${e.message}"
            return ReplyResult(errorMessage = errorMessage)
        } catch (e: Exception) {
            logger.error("Failed to process admin reply: ${e.message}", e)
            val errorMessage = SendMessage()
            errorMessage.setChatId(replyMessage.chatId)
            errorMessage.text = "Failed to send reply: ${e.message}"
            return ReplyResult(errorMessage = errorMessage)
        }
    }

    /**
     * Check if a text contains mentions of Zelensky
     */
    private fun containsZelenskyMention(text: String): Boolean {
        return Regex(".*\\b(?:зеленский|зеленского|зеленским|зеля|зелю|зеле)\\b.*", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }

    /**
     * Create a message to forward to the admin
     */
    private fun createAdminForwardMessage(message: Message): SendMessage {
        val sendMessage = SendMessage()
        sendMessage.setChatId(ADMIN_CHAT_ID)
        sendMessage.text = "${message.chatId}: ${message.from.userName}: ${message.text}: ${message.messageId}"
        return sendMessage
    }

    /**
     * Data class to hold the result of message processing
     */
    data class ProcessingResult(
        var processedText: ProcessedText? = null,
        var jokeResponse: String? = null,
        var adminForwardMessage: SendMessage? = null
    )

    /**
     * Data class to hold the result of handling a reply to a forwarded message
     */
    data class ReplyResult(
        val replyMessage: SendMessage? = null,
        val confirmationMessage: SendMessage? = null,
        val errorMessage: SendMessage? = null
    )
}
