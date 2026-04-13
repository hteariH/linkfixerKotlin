package com.mamoru.service

import com.mamoru.service.MessageCacheService.CachedMessage
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.PhotoSize

data class ImpersonationResponse(
    val text: String,
    val impersonatedUserId: Long? = null
)

interface AIService {

    fun getRandomJoke(chatId: Long? = null): String

    fun generateMentionResponse(
        messageText: String,
        chatId: Long,
        replyText: String? = null,
        from: String? = null,
        replyPhoto: PhotoSize? = null,
        bot: TelegramLongPollingBot? = null,
        botToken: String? = null,
        botUsername: String = "HydraManagerBot"
    ): String

    fun generateImpersonationResponse(
        messageText: String,
        replyText: String? = null,
        from: String? = null,
        replyPhoto: PhotoSize? = null,
        bot: TelegramLongPollingBot? = null,
        botToken: String? = null,
        botUsername: String = "HydraManagerBot",
        userid: Long,
        replyChain: List<CachedMessage> = emptyList(),
        recentMessages: List<CachedMessage> = emptyList()
    ): ImpersonationResponse
}
