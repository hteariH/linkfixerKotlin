package com.mamoru.service

import com.mamoru.service.url.ProcessedText
import com.mamoru.service.url.UrlProcessingPipeline
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import kotlin.random.Random
import org.springframework.beans.factory.annotation.Value
import org.telegram.telegrambots.bots.TelegramLongPollingBot

/**
 * Service for processing Telegram messages
 */
@Service
class MessageProcessorService(
    private val urlProcessingPipeline: UrlProcessingPipeline,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val geminiAIService: GeminiAIService,
    @Value("\${telegram.bot.username:LinkFixer_Bot}") private val botUsername: String
) {
    private val logger = LoggerFactory.getLogger(MessageProcessorService::class.java)

    // Admin chat ID for forwarding messages
    private val ADMIN_CHAT_ID = 123616664L

    /**
     * Process a text message and handle URLs
     *
     * @param message The Telegram message to process
     * @param bot The Telegram bot instance (needed to download photos)
     * @param botToken The Telegram bot token
     * @return ProcessingResult containing the results of processing
     */
    fun processTextMessage(message: Message, bot: TelegramLongPollingBot? = null, botToken: String? = null): ProcessingResult {
        val text = message.text
        val chatId = message.chatId
        val result = ProcessingResult()

        // Check if the bot is mentioned or replied to
        if (isBotMentioned(text) || isBotRepliedTo(message)) {
            // Extract content from the replied message if available
            val replyToMessage = message.replyToMessage
            val from = message.replyToMessage.from.userName
            val replyText = replyToMessage?.text
            val replyPhoto = replyToMessage?.photo?.maxByOrNull { it.fileSize }

            val responseText = geminiAIService.generateMentionResponse(text, chatId, replyText,from, replyPhoto, bot, botToken)
            result.mentionResponse = responseText
            logger.info("Generated response for bot mention/reply in chat $chatId")
        }
        // Check for Zelensky mentions and possibly send a joke
        else if (containsZelenskyMention(text) && 
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
     * Check if the bot is mentioned in the message text
     */
    private fun isBotMentioned(text: String): Boolean {
        return text.contains("@$botUsername", ignoreCase = true)
    }

    /**
     * Check if the message is a reply to a message sent by the bot
     */
    private fun isBotRepliedTo(message: Message): Boolean {
        val replyToMessage = message.replyToMessage ?: return false
        val replyToUser = replyToMessage.from ?: return false
        return replyToUser.userName.equals(botUsername, ignoreCase = true) || 
               replyToUser.isBot && replyToUser.firstName.equals(botUsername, ignoreCase = true)
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
        var mentionResponse: String? = null,
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
