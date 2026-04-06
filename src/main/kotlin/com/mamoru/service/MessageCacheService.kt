package com.mamoru.service

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.ConcurrentHashMap

@Service
class MessageCacheService {

    data class CachedMessage(
        val messageId: Int,
        val text: String?,
        val fromUsername: String?,
        val replyToMessageId: Int?,
        val photoFileId: String? = null
    )

    // chatId -> (messageId -> CachedMessage), capped at maxPerChat entries
    private val cache = ConcurrentHashMap<Long, LinkedHashMap<Int, CachedMessage>>()
    private val maxPerChat = 300

    // tracks message IDs that this bot instance sent (chatId -> set of messageIds)
    private val ownMessages = ConcurrentHashMap<Long, MutableSet<Int>>()

    fun cache(message: Message) {
        val chatMessages = cache.getOrPut(message.chatId) {
            object : LinkedHashMap<Int, CachedMessage>(maxPerChat, 0.75f, false) {
                override fun removeEldestEntry(eldest: Map.Entry<Int, CachedMessage>) = size > maxPerChat
            }
        }
        chatMessages[message.messageId] = CachedMessage(
            messageId = message.messageId,
            text = message.text,
            fromUsername = message.from?.userName,
            replyToMessageId = message.replyToMessage?.messageId,
            photoFileId = message.photo?.maxByOrNull { it.fileSize }?.fileId
        )
    }

    fun trackSentMessage(chatId: Long, messageId: Int) {
        ownMessages.getOrPut(chatId) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }
            .add(messageId)
    }

    fun isOwnMessage(chatId: Long, messageId: Int): Boolean =
        ownMessages[chatId]?.contains(messageId) == true

    /**
     * Returns messages in chronological order (oldest first) up the reply chain,
     * starting from the parent of [messageId]. The message itself is not included.
     */
    fun getReplyChain(chatId: Long, messageId: Int, maxDepth: Int = 10): List<CachedMessage> {
        val chatMessages = cache[chatId] ?: return emptyList()
        val chain = mutableListOf<CachedMessage>()
        var currentId: Int? = chatMessages[messageId]?.replyToMessageId
        var depth = 0
        while (currentId != null && depth < maxDepth) {
            val msg = chatMessages[currentId] ?: break
            chain.add(0, msg)
            currentId = msg.replyToMessageId
            depth++
        }
        return chain
    }
}
