package com.mamoru.service

import com.mamoru.service.url.ProcessedText
import com.mamoru.service.url.UrlProcessingPipeline
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory
import kotlin.random.Random
import org.telegram.telegrambots.bots.TelegramLongPollingBot

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
     * @param bot The Telegram bot instance (needed to download photos)
     * @param botToken The Telegram bot token
     * @param botUsername The username of the bot processing the message
     * @return ProcessingResult containing the results of processing
     */
    fun processTextMessage(
        message: Message,
        bot: TelegramLongPollingBot? = null,
        botToken: String? = null,
        botUsername: String = "LinkFixer_Bot"
    ): ProcessingResult {
        val text = message.text
        val chatId = message.chatId
        val result = ProcessingResult()

        // Check if the bot is mentioned or replied to
        if (message.chatId != -1001329162597) {
            if (isBotMentioned(text, botUsername) || isBotRepliedTo(message, botUsername)) {
                // Extract content from the replied message if available
                val replyToMessage = message.replyToMessage
                val from = message.replyToMessage.from.userName
                val replyText = replyToMessage?.text
                val replyPhoto = replyToMessage?.photo?.maxByOrNull { it.fileSize }

                val responseText =
                    geminiAIService.generateMentionResponse(text, chatId, replyText, from, replyPhoto, bot, botToken, botUsername)
                result.mentionResponse = responseText
                logger.info("Generated response for bot mention/reply in chat $chatId")
            } else if (containsZelenskyMention(text) && // Check for Zelensky mentions and possibly send a joke
                chatSettingsManagementService.getChatSettings(chatId).sendRandomJoke &&
                Random.nextBoolean()
            ) {
                result.jokeResponse = geminiAIService.getRandomJoke(chatId)
                logger.info("Generated joke response for Zelensky mention in chat $chatId")
            }
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
     *
     * @param text The message text to check
     * @param botUsername The username of the bot to check for
     */
    private fun isBotMentioned(text: String, botUsername: String): Boolean {
        return text.contains("@$botUsername", ignoreCase = true)
    }

    /**
     * Check if the message is a reply to a message sent by the bot
     *
     * @param message The message to check
     * @param botUsername The username of the bot to check for
     */
    private fun isBotRepliedTo(message: Message, botUsername: String): Boolean {
        val replyToMessage = message.replyToMessage ?: return false
        val replyToUser = replyToMessage.from ?: return false
        return replyToUser.userName.equals(botUsername, ignoreCase = true) ||
                replyToUser.isBot && replyToUser.firstName.equals(botUsername, ignoreCase = true)
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

}
