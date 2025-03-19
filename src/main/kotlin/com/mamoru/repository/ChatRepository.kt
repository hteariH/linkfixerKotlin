package com.mamoru.repository

import org.springframework.stereotype.Repository
import java.util.concurrent.CopyOnWriteArraySet

@Repository
class ChatRepository {
    private val activeChatIds = CopyOnWriteArraySet<Long>()
    
    fun addChat(chatId: Long) {
        if(isChatActive(chatId)) return
        activeChatIds.add(chatId)
        println("added chat: $chatId")
        println("current active chats: $activeChatIds")
    }
    
    fun getAllChats(): Set<Long> {
        println("returning all chats: $activeChatIds")
        return activeChatIds.toSet()
    }
    fun isChatActive(chatId: Long): Boolean {
        if (activeChatIds.contains(chatId)) {
            return true
        }
        return false
    }
}