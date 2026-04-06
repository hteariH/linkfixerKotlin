package com.mamoru.service

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory
import kotlin.random.Random
import org.telegram.telegrambots.bots.TelegramLongPollingBot

@Service
class MessageProcessorService(
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val geminiAIService: GeminiAIService,
    private val messageCacheService: MessageCacheService
) {
    private val logger = LoggerFactory.getLogger(MessageProcessorService::class.java)

    fun processTextMessage(
        message: Message,
        bot: TelegramLongPollingBot? = null,
        botToken: String? = null,
        botUsername: String = "HydraManagerBot",
        targetUserId: Long? = null,
        replyChain: List<MessageCacheService.CachedMessage> = emptyList()
    ): ProcessingResult {
        val text = message.text
        val chatId = message.chatId
        val result = ProcessingResult()

        val settings = chatSettingsManagementService.getChatSettings(chatId)
        val isManaged = targetUserId != null
        val isPrivateChat = message.chat.isUserChat
        val isReplyToOwnMessage = isManaged && !isPrivateChat &&
            message.replyToMessage?.messageId?.let { messageCacheService.isOwnMessage(message.chatId, it) } == true
        val isMentioned = isBotMentioned(text, botUsername) || isBotRepliedTo(message, botUsername) ||
            (isManaged && isPrivateChat) || isReplyToOwnMessage

        if (isMentioned && (isManaged || settings.commentOnPictures)) {
            val replyToMessage = message.replyToMessage
            val from = replyToMessage?.from?.userName
            val replyText = replyToMessage?.text
            val replyPhoto = replyToMessage?.photo?.maxByOrNull { it.fileSize }
            val cleanText = text.replace("@$botUsername", "", ignoreCase = true).trim()

            if (isManaged) {
                // Managed bot: impersonate the linked user in any chat
                val response = geminiAIService.generateImpersonationResponse(
                    cleanText, replyText, from, replyPhoto, bot, botToken, botUsername, targetUserId!!, replyChain
                )
                result.mentionResponse = response.text
                result.impersonatedUserId = response.impersonatedUserId
                logger.info("Generated impersonation response as user $targetUserId in chat $chatId")
            } else {
                // Main bot: generic mention response
                result.mentionResponse = geminiAIService.generateMentionResponse(
                    cleanText, chatId, replyText, from, replyPhoto, bot, botToken, botUsername
                )
                logger.info("Generated mention response in chat $chatId")
            }
        } else if (!isManaged && !isMentioned && settings.commentOnPictures &&
            containsZelenskyMention(text) && settings.sendRandomJoke && Random.nextBoolean()
        ) {
            result.jokeResponse = geminiAIService.getRandomJoke(chatId)
            logger.info("Generated joke response for Zelensky mention in chat $chatId")
        }

        chatSettingsManagementService.addChat(chatId)
        return result
    }

    private fun isBotMentioned(text: String, botUsername: String): Boolean =
        text.contains("@$botUsername", ignoreCase = true)

    private fun isBotRepliedTo(message: Message, botUsername: String): Boolean {
        val replyToUser = message.replyToMessage?.from ?: return false
        return replyToUser.userName.equals(botUsername, ignoreCase = true) ||
                replyToUser.isBot && replyToUser.userName.equals(botUsername, ignoreCase = true)
    }

    private fun containsZelenskyMention(text: String): Boolean =
        Regex(".*\\b(?:зеленский|зеленського|зеленским|зеля|зелю|зеле)\\b.*", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    data class ProcessingResult(
        var jokeResponse: String? = null,
        var mentionResponse: String? = null,
        var impersonatedUserId: Long? = null
    )
}
