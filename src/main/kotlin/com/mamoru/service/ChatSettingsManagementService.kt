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
            val newSettings =
                ChatSettings(
                    chatId, 
                    sendCounterUntilWin = false, 
                    sendRandomJoke = false, 
                    commentOnPictures = false
                    // Default values for jokePrompt and picturePrompt will be used
                )
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
            chatJpaRepository.save(
                ChatSettings(
                    chatId,
                    sendCounter,
                    settings.sendRandomJoke,
                    settings.commentOnPictures,
                    true,
                    settings.jokePrompt,
                    settings.picturePrompt
                )
            )
        }
    }

    @Transactional
    fun updateSendJoke(chatId: Long, newSetting: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.sendRandomJoke != newSetting) {
            chatJpaRepository.save(
                ChatSettings(
                    chatId,
                    settings.sendCounterUntilWin,
                    newSetting,
                    settings.commentOnPictures,
                    settings.transcribeAudio,
                    settings.jokePrompt,
                    settings.picturePrompt
                )
            )
        }
    }

    @Transactional
    fun updateCommentOnPictures(chatId: Long, newSetting: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.commentOnPictures != newSetting) {
            chatJpaRepository.save(
                ChatSettings(
                    chatId,
                    settings.sendCounterUntilWin,
                    settings.sendRandomJoke,
                    newSetting,
                    settings.transcribeAudio,
                    settings.jokePrompt,
                    settings.picturePrompt
                )
            )
        }
    }

    /**
     * Update the joke prompt for a specific chat
     */
    @Transactional
    fun updateJokePrompt(chatId: Long, jokePrompt: String) {
        val settings = getChatSettings(chatId)
        if (settings.jokePrompt != jokePrompt) {
            chatJpaRepository.save(
                ChatSettings(
                    chatId,
                    settings.sendCounterUntilWin,
                    settings.sendRandomJoke,
                    settings.commentOnPictures,
                    settings.transcribeAudio,
                    jokePrompt,
                    settings.picturePrompt
                )
            )
        }
    }

    /**
     * Update the picture comment prompt for a specific chat
     */
    @Transactional
    fun updatePicturePrompt(chatId: Long, picturePrompt: String) {
        val settings = getChatSettings(chatId)
        if (settings.picturePrompt != picturePrompt) {
            chatJpaRepository.save(
                ChatSettings(
                    chatId,
                    settings.sendCounterUntilWin,
                    settings.sendRandomJoke,
                    settings.commentOnPictures,
                    settings.transcribeAudio,
                    settings.jokePrompt,
                    picturePrompt
                )
            )
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
                chatJpaRepository.save(ChatSettings(
                    chatId
                    // Default values for all other fields will be used
                ))
            }
        }
    }

    /**
     * Update whether audio messages should be transcribed for a specific chat
     */
    @Transactional
    fun updateTranscribeAudio(chatId: Long, newSetting: Boolean) {
        val settings = getChatSettings(chatId)
        if (settings.transcribeAudio != newSetting) {
            val updatedSettings = settings.copy(transcribeAudio = newSetting)
            chatJpaRepository.save(updatedSettings)
        }
    }


}
