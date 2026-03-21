package com.mamoru.service

import com.mamoru.entity.ChatSettings
import com.mamoru.repository.ChatSettingsRepository
import org.springframework.stereotype.Service

@Service
class ChatSettingsService(private val chatSettingsRepository: ChatSettingsRepository) {

    fun getChatSettings(chatId: Long): ChatSettings {
        return chatSettingsRepository.findById(chatId).orElseGet {
            val settings = ChatSettings(chatId = chatId)
            chatSettingsRepository.save(settings)
            settings
        }
    }
    fun updateEnableRevival(chatId: Long, enabled: Boolean) {
        val settings = getChatSettings(chatId)
        chatSettingsRepository.save(settings.copy(enableRevival = enabled))
    }
}
