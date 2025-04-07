package com.mamoru.service

import com.mamoru.entity.ChatSettings
import com.mamoru.repository.ChatJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatSettingsManagementService(private val chatJpaRepository: ChatJpaRepository) {

    /**
     * Get the chat settings for a specific chat
     * If the chat doesn't exist in the database, it creates a new entry with default settings
     */
    fun getChatSettings(chatId: Long): ChatSettings {
        return chatJpaRepository.findById(chatId).orElseGet {
            val newSettings = ChatSettings(chatId)
            chatJpaRepository.save(newSettings)
            newSettings
        }
    }

    /**
     * Update whether the counter until win should be sent for a specific chat
     */
    @Transactional
    fun updateSendCounterUntilWin(chatId: Long, sendCounter: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.sendCounterUntilWin != sendCounter) {
            chatJpaRepository.save(ChatSettings(chatId, sendCounter))
        }
    }

    @Transactional
    fun updateSendJoke(chatId: Long, newSetting: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.sendRandomJoke != newSetting) {
            chatJpaRepository.save(ChatSettings(chatId, newSetting))
        }
    }

    /**
     * Get all chats with their settings
     */
    fun getAllChats(): List<ChatSettings> {
        return chatJpaRepository.findAll()
    }
    
    /**
     * Get all chats where sendCounterUntilWin is true
     */
    fun getChatsWithSendCounter(): List<ChatSettings> {
        return chatJpaRepository.findAll().filter { it.sendCounterUntilWin }
    }
    
    /**
     * Get all chats where sendCounterUntilWin is false
     */
    fun getChatsWithoutSendCounter(): List<ChatSettings> {
        return chatJpaRepository.findAll().filter { !it.sendCounterUntilWin }
    }
    @Transactional
    fun addChat(chatId: Long?) {
        if (chatId != null) {
            val findByChatId = chatJpaRepository.findByChatId(chatId)
            if (findByChatId == null) {
                chatJpaRepository.save(ChatSettings(chatId, true))
            }
        }
    }


}