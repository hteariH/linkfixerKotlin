package com.mamoru.service

import com.mamoru.util.Constants
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory

/**
 * Service for handling Telegram bot commands
 */
@Service
class CommandHandlerService(
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val geminiAIService: GeminiAIService
) {
    private val logger = LoggerFactory.getLogger(CommandHandlerService::class.java)

    /**
     * Process a command message and return the appropriate response
     *
     * @param message The Telegram message containing the command
     * @return CommandResult containing the response text and any additional data
     */
    fun handleCommand(message: Message): CommandResult {
        val chatId = message.chatId
        val text = message.text

        return when {
            text.startsWith(Constants.Command.TOGGLE_COUNTER, ignoreCase = true) -> {
                handleToggleCounter(chatId)
            }
            text.startsWith(Constants.Command.TOGGLE_JOKE, ignoreCase = true) -> {
                handleToggleJoke(chatId)
            }
            text.startsWith(Constants.Command.TOGGLE_PICTURE_COMMENT, ignoreCase = true) -> {
                handleTogglePictureComment(chatId)
            }
            text.startsWith(Constants.Command.TOGGLE_AUDIO_TRANSCRIPTION, ignoreCase = true) -> {
                handleToggleAudioTranscription(chatId)
            }
            text.startsWith(Constants.Command.GET_RANDOM_JOKE, ignoreCase = true) -> {
                handleGetRandomJoke(chatId)
            }
            text.startsWith(Constants.Command.SET_JOKE_PROMPT, ignoreCase = true) -> {
                handleSetJokePrompt(chatId, text)
            }
            text.startsWith(Constants.Command.SET_PICTURE_PROMPT, ignoreCase = true) -> {
                handleSetPicturePrompt(chatId, text)
            }
            else -> {
                CommandResult(isCommand = false)
            }
        }
    }

    private fun handleToggleCounter(chatId: Long): CommandResult {
        val currentSettings = chatSettingsManagementService.getChatSettings(chatId)
        val newSetting = !currentSettings.sendCounterUntilWin
        chatSettingsManagementService.updateSendCounterUntilWin(chatId, newSetting)

        val responseText = if (newSetting) {
            Constants.Message.COUNTER_ENABLED
        } else {
            Constants.Message.COUNTER_DISABLED
        }

        logger.info("Updated sendCounterUntilWin setting for chat $chatId to $newSetting")
        return CommandResult(isCommand = true, responseText = responseText)
    }

    private fun handleToggleJoke(chatId: Long): CommandResult {
        val currentSettings = chatSettingsManagementService.getChatSettings(chatId)
        val newSetting = !currentSettings.sendRandomJoke
        chatSettingsManagementService.updateSendJoke(chatId, newSetting)

        val responseText = if (newSetting) {
            Constants.Message.JOKE_ENABLED
        } else {
            Constants.Message.JOKE_DISABLED
        }

        logger.info("Updated sendRandomJoke setting for chat $chatId to $newSetting")
        return CommandResult(isCommand = true, responseText = responseText)
    }

    private fun handleTogglePictureComment(chatId: Long): CommandResult {
        val currentSettings = chatSettingsManagementService.getChatSettings(chatId)
        val newSetting = !currentSettings.commentOnPictures
        chatSettingsManagementService.updateCommentOnPictures(chatId, newSetting)

        val responseText = if (newSetting) {
            Constants.Message.PICTURE_COMMENT_ENABLED
        } else {
            Constants.Message.PICTURE_COMMENT_DISABLED
        }

        logger.info("Updated commentOnPictures setting for chat $chatId to $newSetting")
        return CommandResult(isCommand = true, responseText = responseText)
    }

    private fun handleToggleAudioTranscription(chatId: Long): CommandResult {
        val currentSettings = chatSettingsManagementService.getChatSettings(chatId)
        val newSetting = !currentSettings.transcribeAudio
        chatSettingsManagementService.updateTranscribeAudio(chatId, newSetting)

        val responseText = if (newSetting) {
            Constants.Message.AUDIO_TRANSCRIPTION_ENABLED
        } else {
            Constants.Message.AUDIO_TRANSCRIPTION_DISABLED
        }

        logger.info("Updated transcribeAudio setting for chat $chatId to $newSetting")
        return CommandResult(isCommand = true, responseText = responseText)
    }

    private fun handleGetRandomJoke(chatId: Long): CommandResult {
        val joke = geminiAIService.getRandomJoke(chatId)
        logger.info("Generated random joke for chat $chatId")
        return CommandResult(isCommand = true, responseText = joke)
    }

    private fun handleSetJokePrompt(chatId: Long, text: String): CommandResult {
        val prompt = text.substringAfter(Constants.Command.SET_JOKE_PROMPT).trim()
        return if (prompt.isNotEmpty()) {
            chatSettingsManagementService.updateJokePrompt(chatId, prompt)
            logger.info("Updated joke prompt for chat $chatId")
            CommandResult(isCommand = true, responseText = Constants.Message.JOKE_PROMPT_UPDATED)
        } else {
            CommandResult(
                isCommand = true,
                responseText = Constants.Message.JOKE_PROMPT_HELP
            )
        }
    }

    private fun handleSetPicturePrompt(chatId: Long, text: String): CommandResult {
        val prompt = text.substringAfter(Constants.Command.SET_PICTURE_PROMPT).trim()
        return if (prompt.isNotEmpty()) {
            chatSettingsManagementService.updatePicturePrompt(chatId, prompt)
            logger.info("Updated picture prompt for chat $chatId")
            CommandResult(isCommand = true, responseText = Constants.Message.PICTURE_PROMPT_UPDATED)
        } else {
            CommandResult(
                isCommand = true,
                responseText = Constants.Message.PICTURE_PROMPT_HELP
            )
        }
    }

    /**
     * Data class to hold the result of command processing
     */
    data class CommandResult(
        val isCommand: Boolean,
        val responseText: String? = null
    )
}
