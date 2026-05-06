package com.mamoru.service

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.generics.TelegramClient
import org.slf4j.LoggerFactory
import kotlin.random.Random

@Service
class MessageProcessorService(
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val aiService: AIService,
    private val messageCacheService: MessageCacheService,
    private val botRegistryService: BotRegistryService
) {
    private val logger = LoggerFactory.getLogger(MessageProcessorService::class.java)

    fun processTextMessage(
        message: Message,
        botUsername: String = "HydraManagerBot",
        targetUserId: Long? = null,
        replyChain: List<MessageCacheService.CachedMessage> = emptyList(),
        recentMessages: List<MessageCacheService.CachedMessage> = emptyList(),
        telegramClient: TelegramClient? = null
    ): ProcessingResult {
        val text = message.text ?: message.caption ?: ""
        val chatId = message.chatId
        val result = ProcessingResult()

        val settings = chatSettingsManagementService.getChatSettings(chatId)
        val isManaged = targetUserId != null
        val isPrivateChat = message.chat.isUserChat

        val byMention = isBotMentioned(text, botUsername)
        val byReply = isBotRepliedTo(message, botUsername)
        val byPrivate = isManaged && isPrivateChat
        val byOwnMessage = isManaged && !isPrivateChat &&
            message.replyToMessage?.messageId?.let { messageCacheService.isOwnMessage(botUsername, message.chatId, it) } == true

        val senderUsername = message.from?.userName
        val isFromAnotherBot = botRegistryService.isBot(senderUsername) &&
                              !senderUsername.equals(botUsername, ignoreCase = true)

        var isMentioned = byMention || byReply || byPrivate || byOwnMessage

        if (isMentioned && isFromAnotherBot) {
            val chain = messageCacheService.getReplyChain(chatId, message.messageId)
            val botMessageCount = chain.count { botRegistryService.isBot(it.fromUsername) }

            val probability = when {
                botMessageCount >= 6 -> 0.05
                botMessageCount >= 4 -> 0.2
                botMessageCount >= 2 -> 0.55
                else -> 0.75
            }

            val shouldRespond = Random.nextDouble() < probability
            logger.info("[{}] Bot-to-bot interaction detected. Sender: @{}. Bot msgs in chain: {}. Probability: {}. Should respond: {}",
                botUsername, senderUsername, botMessageCount, probability, shouldRespond)
            Thread.sleep(10000)
            if (!shouldRespond) {
                isMentioned = false
            }
        }

        if (isManaged) {
            logger.debug(
                "[{}] chat={} msgId={} replyToId={} text='{}' | " +
                "byMention={} byReply={} byPrivate={} byOwnMsg={} → isMentioned={}",
                botUsername, chatId, message.messageId,
                message.replyToMessage?.messageId,
                text.take(80),
                byMention, byReply, byPrivate, byOwnMessage, isMentioned
            )
        }

        if (isMentioned && (isManaged || settings.commentOnPictures)) {
            val replyToMessage = message.replyToMessage
            val from = replyToMessage?.from?.userName
            val replyText = replyToMessage?.text ?: replyToMessage?.caption
            val replyPhoto = replyToMessage?.photo?.maxByOrNull { it.fileSize }
            val cleanText = text.replace("@$botUsername", "", ignoreCase = true).trim()

            if (isManaged) {
                logger.debug("[{}] Calling impersonation for userId={}, replyChain={} msgs, cleanText='{}'",
                    botUsername, targetUserId, replyChain.size, cleanText.take(80))
                val response = aiService.generateImpersonationResponse(
                    cleanText, replyText, from, replyPhoto,
                    telegramClient, botUsername, targetUserId!!,
                    replyChain, recentMessages
                )
                result.mentionResponse = response.text
                result.impersonatedUserId = response.impersonatedUserId
                logger.info("[{}] Generated impersonation response as user {} in chat {}", botUsername, targetUserId, chatId)
            } else {
                result.mentionResponse = aiService.generateMentionResponse(
                    cleanText, chatId, replyText, from, replyPhoto,
                    telegramClient, botUsername
                )
                logger.info("Generated mention response in chat $chatId")
            }
        } else if (isManaged && !isMentioned) {
            logger.debug("[{}] Skipping — not addressed (chat={} msgId={})", botUsername, chatId, message.messageId)
        } else if (!isManaged && !isMentioned && settings.commentOnPictures &&
            containsZelenskyMention(text) && settings.sendRandomJoke && Random.nextBoolean()
        ) {
            result.jokeResponse = aiService.getRandomJoke(chatId)
            logger.info("Generated joke response for Zelensky mention in chat $chatId")
        }

        chatSettingsManagementService.addChat(chatId)
        return result
    }

    private fun isBotMentioned(text: String, botUsername: String): Boolean =
        text.contains("@$botUsername", ignoreCase = true)

    private fun isBotRepliedTo(message: Message, botUsername: String): Boolean {
        val replyToUser = message.replyToMessage?.from ?: return false
        return replyToUser.userName?.equals(botUsername, ignoreCase = true) == true
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
