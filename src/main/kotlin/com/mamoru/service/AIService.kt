package com.mamoru.service

import com.mamoru.service.MessageCacheService.CachedMessage
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize
import org.telegram.telegrambots.meta.generics.TelegramClient

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
        telegramClient: TelegramClient? = null,
        botUsername: String = "HydraManagerBot"
    ): String

    fun generateImpersonationResponse(
        messageText: String,
        replyText: String? = null,
        from: String? = null,
        replyPhoto: PhotoSize? = null,
        telegramClient: TelegramClient? = null,
        botUsername: String = "HydraManagerBot",
        userid: Long,
        replyChain: List<CachedMessage> = emptyList(),
        recentMessages: List<CachedMessage> = emptyList()
    ): ImpersonationResponse
}
