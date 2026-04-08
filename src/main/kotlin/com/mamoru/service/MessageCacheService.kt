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
        val fromFirstName: String? = null,
        val replyToMessageId: Int?,
        val photoFileId: String? = null
    ) {
        fun displayName(): String = when {
            fromUsername != null && fromFirstName != null -> "$fromFirstName (@$fromUsername)"
            fromUsername != null -> "@$fromUsername"
            fromFirstName != null -> fromFirstName
            else -> "unknown"
        }
    }

    // chatId -> (messageId -> CachedMessage), capped at maxPerChat entries
    private val cache = ConcurrentHashMap<Long, LinkedHashMap<Int, CachedMessage>>()
    private val maxPerChat = 100

    // botUsername (lowercase) -> chatId -> set of sent messageIds
    private val ownMessages = ConcurrentHashMap<String, ConcurrentHashMap<Long, MutableSet<Int>>>()

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
            fromFirstName = message.from?.firstName,
            replyToMessageId = message.replyToMessage?.messageId,
            photoFileId = message.photo?.maxByOrNull { it.fileSize }?.fileId
        )
    }

    fun cacheSentMessage(chatId: Long, messageId: Int, text: String?, botUsername: String, replyToMessageId: Int? = null) {
        val chatMessages = cache.getOrPut(chatId) {
            object : LinkedHashMap<Int, CachedMessage>(maxPerChat, 0.75f, false) {
                override fun removeEldestEntry(eldest: Map.Entry<Int, CachedMessage>) = size > maxPerChat
            }
        }
        chatMessages[messageId] = CachedMessage(
            messageId = messageId,
            text = text,
            fromUsername = botUsername,
            replyToMessageId = replyToMessageId
        )
        ownMessages
            .getOrPut(botUsername.lowercase()) { ConcurrentHashMap() }
            .getOrPut(chatId) { java.util.Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(messageId)
    }

    fun trackSentMessage(botUsername: String, chatId: Long, messageId: Int) {
        ownMessages
            .getOrPut(botUsername.lowercase()) { ConcurrentHashMap() }
            .getOrPut(chatId) { java.util.Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(messageId)
    }

    fun isOwnMessage(botUsername: String, chatId: Long, messageId: Int): Boolean =
        ownMessages[botUsername.lowercase()]?.get(chatId)?.contains(messageId) == true

    /**
     * Returns the last [limit] messages in this chat in chronological order (oldest first),
     * excluding the given [excludeIds] (e.g. current message + reply chain already passed separately).
     */
    fun getRecentMessages(chatId: Long, limit: Int, excludeIds: Set<Int> = emptySet()): List<CachedMessage> {
        val chatMessages = cache[chatId] ?: return emptyList()
        return chatMessages.values
            .filter { it.messageId !in excludeIds }
            .takeLast(limit)
    }

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
