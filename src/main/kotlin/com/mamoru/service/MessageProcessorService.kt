package com.mamoru.service

import com.mamoru.service.url.ProcessedText
import com.mamoru.service.url.UrlProcessingPipeline
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.random.Random
import org.telegram.telegrambots.bots.TelegramLongPollingBot

/**
 * Service for processing Telegram messages
 */
@Service
class MessageProcessorService(
    private val urlProcessingPipeline: UrlProcessingPipeline,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val geminiAIService: GeminiAIService,
    private val redisTemplate: StringRedisTemplate
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
//        if (chatSettingsManagementService.getChatSettings(chatId).commentOnPictures) {
//            if (isBotMentioned(text, botUsername) || isBotRepliedTo(message, botUsername)) {
//                // Extract content from the replied message if available
//                val replyToMessage = message.replyToMessage
//                val from = replyToMessage?.from?.userName
//                val replyText = replyToMessage?.text
//                val replyPhoto = replyToMessage?.photo?.maxByOrNull { it.fileSize }
//
//
//                // Check if this is the target chat for impersonation
//                val responseResult = when (chatId) {
//                    geminiAIService.getTargetChatId() -> {
//                        // Use impersonation response for the target chat
//                        // Determine target user ID based on command in text; fallback to Redis-stored value if reply exists; finally random
//                        val redisTargetUserId = message.replyToMessage?.messageId?.let { replyId ->
//                            try {
//                                val toLongOrNull = redisTemplate.opsForValue().get(replyId.toString())?.toLongOrNull()
//                                logger.info("Fetched targetUserId $toLongOrNull from Redis for messageId $replyId")
//                                toLongOrNull
//                            } catch (e: Exception) {
//                                logger.error("Failed to fetch targetUserId from Redis for messageId $replyId: ${e.message}")
//                                null
//                            }
//                        }
//
//                        val targetUserId = when {
//                            text.contains("/kiok@ChatManagerAssistantBot", ignoreCase = true) -> 426020724L
//                            text.contains("/wirewood@ChatManagerAssistantBot", ignoreCase = true) -> 189786389L
//                            text.contains("/tro@ChatManagerAssistantBot", ignoreCase = true) -> 114725695L
//                            text.contains("/simple@ChatManagerAssistantBot", ignoreCase = true) -> 317051301L
//                            text.contains("/berkut@ChatManagerAssistantBot", ignoreCase = true) -> 123616664L
//                            text.contains("/jotun@ChatManagerAssistantBot", ignoreCase = true) -> 158637780L
//                            text.contains("/eevee@ChatManagerAssistantBot", ignoreCase = true) -> 179935044L
//                            text.contains("/death@ChatManagerAssistantBot", ignoreCase = true) -> 141136819L
//                            redisTargetUserId != null -> redisTargetUserId
//                            else -> {
//                                val nextInt = Random.nextInt(8)
//                                val listOf = listOf(426020724L, 189786389L, 114725695L, 158637780L, 317051301L, 123616664L, 179935044L,141136819L)
//                                listOf[nextInt]
//                            }
//                        }
//
//                        val replace = text.replace(
//                            Regex("/(?:kiok|wirewood|tro|simple|berkut|jotun|eevee|death)@ChatManagerAssistantBot", RegexOption.IGNORE_CASE),
//                            ""
//                        )
//
//                        val impersonationResponse = geminiAIService.generateImpersonationResponse(
//                            replace, replyText, from, replyPhoto, bot, botToken, botUsername, targetUserId
//                        )
//                        logger.info("Generated impersonation response for bot mention/reply in target chat $chatId with targetUserId=$targetUserId")
//                        impersonationResponse
//                    }
//                    // чайка таун
//                    -1001706199236 -> {
//                        val impersonationResponse = geminiAIService.generateImpersonationResponse(text, replyText, from, replyPhoto, bot, botToken, botUsername, 515794581)//влад
////                        val impersonationResponse = geminiAIService.generateImpersonationResponse(text, replyText, from, replyPhoto, bot, botToken, botUsername, 455020673)//старлайт
//                        logger.info("Generated impersonation response for bot mention/reply in target chat $chatId")
//                        impersonationResponse
//                    }
//
//                    -1002920837282 -> {
//                        val impersonationResponse = geminiAIService.generateImpersonationResponse(text, replyText, from, replyPhoto, bot, botToken, botUsername, 155189941)//artem
////                        val impersonationResponse = geminiAIService.generateImpersonationResponse(text, replyText, from, replyPhoto, bot, botToken, botUsername, 624397093)//glist
////                        val impersonationResponse = geminiAIService.generateImpersonationResponse(text, replyText, from, replyPhoto, bot, botToken, botUsername, 6374805034)//sammy
////                        val impersonationResponse = geminiAIService.generateImpersonationResponse(text, replyText, from, replyPhoto, bot, botToken, botUsername, 383133167)//igor
//                        logger.info("Generated impersonation response for bot mention/reply in target chat $chatId")
//                        impersonationResponse
//                    }
//
//                    else -> {
//                        // Use regular mention response for other chats
//                        val mentionResponse = geminiAIService.generateMentionResponse(
//                            text,
//                            chatId,
//                            replyText,
//                            from,
//                            replyPhoto,
//                            bot,
//                            botToken,
//                            botUsername
//                        )
//                        ImpersonationResponse(mentionResponse)
//                    }
//                }
//                result.mentionResponse = responseResult.text
//                result.impersonatedUserId = responseResult.impersonatedUserId
//                logger.info("Generated response for bot mention/reply in chat $chatId")
//
//            } else if (containsZelenskyMention(text) && // Check for Zelensky mentions and possibly send a joke
//                chatSettingsManagementService.getChatSettings(chatId).sendRandomJoke &&
//                Random.nextBoolean()
//            ) {
//                result.jokeResponse = geminiAIService.getRandomJoke(chatId)
//                logger.info("Generated joke response for Zelensky mention in chat $chatId")
//            }
//        }
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
        sendMessage.text = "${message.chatId}: ${message.from?.userName ?: "unknown"}: ${message.text}: ${message.messageId}"
        return sendMessage
    }

    /**
     * Data class to hold the result of message processing
     */
    data class ProcessingResult(
        var processedText: ProcessedText? = null,
        var jokeResponse: String? = null,
        var mentionResponse: String? = null,
        var impersonatedUserId: Long? = null,
        var adminForwardMessage: SendMessage? = null
    )

}
