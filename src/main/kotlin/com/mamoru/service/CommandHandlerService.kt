package com.mamoru.service

import com.mamoru.util.Constants
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import org.slf4j.LoggerFactory

@Service
class CommandHandlerService(
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val aiService: AIService,
    @Lazy private val managedBotService: ManagedBotService
) {
    private val logger = LoggerFactory.getLogger(CommandHandlerService::class.java)

    fun handleCommand(message: Message): CommandResult {
        val chatId = message.chatId
        val text = message.text

        return when {
            text.startsWith(Constants.Command.TOGGLE_JOKE, ignoreCase = true) -> handleToggleJoke(chatId)
            text.startsWith(Constants.Command.TOGGLE_PICTURE_COMMENT, ignoreCase = true) -> handleTogglePictureComment(chatId)
            text.startsWith(Constants.Command.GET_RANDOM_JOKE, ignoreCase = true) -> handleGetRandomJoke(chatId)
            text.startsWith(Constants.Command.SET_JOKE_PROMPT, ignoreCase = true) -> handleSetJokePrompt(chatId, text)
            text.startsWith(Constants.Command.SET_PICTURE_PROMPT, ignoreCase = true) -> handleSetPicturePrompt(chatId, text)
            text.startsWith(Constants.Command.CREATE_BOT, ignoreCase = true) -> handleCreateBot(text, message.chatId)
            text.startsWith(Constants.Command.ACTIVATE_BOT, ignoreCase = true) -> handleActivateBot(text)
            else -> CommandResult(isCommand = false)
        }
    }

    private fun handleToggleJoke(chatId: Long): CommandResult {
        val currentSettings = chatSettingsManagementService.getChatSettings(chatId)
        val newSetting = !currentSettings.sendRandomJoke
        chatSettingsManagementService.updateSendJoke(chatId, newSetting)
        val responseText = if (newSetting) Constants.Message.JOKE_ENABLED else Constants.Message.JOKE_DISABLED
        logger.info("Updated sendRandomJoke setting for chat $chatId to $newSetting")
        return CommandResult(isCommand = true, responseText = responseText)
    }

    private fun handleTogglePictureComment(chatId: Long): CommandResult {
        val currentSettings = chatSettingsManagementService.getChatSettings(chatId)
        val newSetting = !currentSettings.commentOnPictures
        chatSettingsManagementService.updateCommentOnPictures(chatId, newSetting)
        val responseText = if (newSetting) Constants.Message.PICTURE_COMMENT_ENABLED else Constants.Message.PICTURE_COMMENT_DISABLED
        logger.info("Updated commentOnPictures setting for chat $chatId to $newSetting")
        return CommandResult(isCommand = true, responseText = responseText)
    }

    private fun handleGetRandomJoke(chatId: Long): CommandResult {
        val joke = aiService.getRandomJoke(chatId)
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
            CommandResult(isCommand = true, responseText = Constants.Message.JOKE_PROMPT_HELP)
        }
    }

    private fun handleSetPicturePrompt(chatId: Long, text: String): CommandResult {
        val prompt = text.substringAfter(Constants.Command.SET_PICTURE_PROMPT).trim()
        return if (prompt.isNotEmpty()) {
            chatSettingsManagementService.updatePicturePrompt(chatId, prompt)
            logger.info("Updated picture prompt for chat $chatId")
            CommandResult(isCommand = true, responseText = Constants.Message.PICTURE_PROMPT_UPDATED)
        } else {
            CommandResult(isCommand = true, responseText = Constants.Message.PICTURE_PROMPT_HELP)
        }
    }

    private fun handleCreateBot(text: String, chatId: Long): CommandResult {
        val args = parseArgs(text, Constants.Command.CREATE_BOT)
        if (args.size < 2) {
            return CommandResult(
                isCommand = true,
                responseText = "Usage: /createBot <suggestedBotUsername> <targetUsername|userId>\n" +
                    "Example: /createBot KiokBot kiok\n" +
                    "Example: /createBot KiokBot 426020724"
            )
        }
        val suggestedUsername = args[0]
        val targetUsername = args[1].removePrefix("@")

        managedBotService.storePendingCreation(suggestedUsername, targetUsername, chatId)
        val link = managedBotService.generateCreationLink(suggestedUsername)
        logger.info("Generated creation link for managed bot $suggestedUsername -> @$targetUsername")
        return CommandResult(
            isCommand = true,
            responseText = "Use this link to create the bot:\n$link\n\n" +
                "The bot will activate automatically once created.\n" +
                "If that fails, run: /activateBot $suggestedUsername $targetUsername"
        )
    }

    private fun handleActivateBot(text: String): CommandResult {
        val args = parseArgs(text, Constants.Command.ACTIVATE_BOT)
        if (args.size < 2) {
            return CommandResult(
                isCommand = true,
                responseText = "Usage: /activateBot <botUsername> <targetUsername|userId> [botId]\n" +
                    "Example: /activateBot KiokBot kiok\n" +
                    "Example: /activateBot KiokBot kiok 7654321098"
            )
        }
        val botUsername = args[0].removePrefix("@")
        val targetUsername = args[1].removePrefix("@")
        val botId = args.getOrNull(2)?.toLongOrNull()

        logger.info("Activating managed bot $botUsername for @$targetUsername (botId=$botId)")
        val result = managedBotService.activateManagedBot(botUsername, targetUsername, botId)
        return CommandResult(isCommand = true, responseText = result)
    }

    /**
     * Parses arguments after a command, stripping the optional @BotName suffix.
     * Handles both "/cmd arg1 arg2" and "/cmd@BotName arg1 arg2" formats.
     */
    private fun parseArgs(text: String, command: String): List<String> {
        val afterCommand = text.substringAfter(command, "")
        val withoutBotSuffix = if (afterCommand.startsWith("@")) {
            afterCommand.substringAfter(" ", "").trim()
        } else {
            afterCommand.trim()
        }
        return withoutBotSuffix.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    data class CommandResult(
        val isCommand: Boolean,
        val responseText: String? = null
    )
}
