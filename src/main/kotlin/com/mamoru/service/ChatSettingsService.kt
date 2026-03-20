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

    fun updateCommentOnPictures(chatId: Long, enabled: Boolean) {
        val settings = getChatSettings(chatId)
        chatSettingsRepository.save(settings.copy(commentOnPictures = enabled))
    }

    fun updatePicturePrompt(chatId: Long, prompt: String) {
        val settings = getChatSettings(chatId)
        chatSettingsRepository.save(settings.copy(picturePrompt = prompt))
    }

    fun updateImpersonateUser(chatId: Long, userId: Long, username: String) {
        val settings = getChatSettings(chatId)
        chatSettingsRepository.save(settings.copy(impersonateUserId = userId, impersonateUsername = username))
    }
}
