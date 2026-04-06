package com.mamoru.service

import com.mamoru.entity.ChatSettings
import com.mamoru.repository.ChatRepository
import org.springframework.stereotype.Service

@Service
class ChatSettingsManagementService(private val chatRepository: ChatRepository) {

    fun getChatSettings(chatId: Long): ChatSettings {
        return chatRepository.findById(chatId).orElseGet {
            val newSettings = ChatSettings(chatId)
            chatRepository.save(newSettings)
            newSettings
        }
    }

    fun updateSendJoke(chatId: Long, newSetting: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.sendRandomJoke != newSetting) {
            chatRepository.save(settings.copy(sendRandomJoke = newSetting))
        }
    }

    fun updateCommentOnPictures(chatId: Long, newSetting: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.commentOnPictures != newSetting) {
            chatRepository.save(settings.copy(commentOnPictures = newSetting))
        }
    }

    fun updateJokePrompt(chatId: Long, jokePrompt: String) {
        val settings = getChatSettings(chatId)
        if (settings.jokePrompt != jokePrompt) {
            chatRepository.save(settings.copy(jokePrompt = jokePrompt))
        }
    }

    fun updatePicturePrompt(chatId: Long, picturePrompt: String) {
        val settings = getChatSettings(chatId)
        if (settings.picturePrompt != picturePrompt) {
            chatRepository.save(settings.copy(picturePrompt = picturePrompt))
        }
    }

    fun getAllChats(): List<ChatSettings> = chatRepository.findAll()

    fun addChat(chatId: Long?) {
        if (chatId != null && chatRepository.findByChatId(chatId) == null) {
            chatRepository.save(ChatSettings(chatId))
        }
    }
}
