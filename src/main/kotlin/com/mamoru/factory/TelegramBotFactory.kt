package com.mamoru.factory

import com.mamoru.LinkFixerBot
import com.mamoru.config.TelegramBotConfig
import com.mamoru.service.ChatSettingsManagementService
import com.mamoru.service.CommandHandlerService
import com.mamoru.service.MediaHandlerService
import com.mamoru.service.MessageProcessorService
import org.springframework.stereotype.Component

/**
 * Factory for creating Telegram bot instances
 */
@Component
class TelegramBotFactory(
    private val commandHandlerService: CommandHandlerService,
    private val mediaHandlerService: MediaHandlerService,
    private val messageProcessorService: MessageProcessorService,
    private val chatSettingsManagementService: ChatSettingsManagementService
) {

    /**
     * Create a LinkFixerBot instance with the given name and token
     */
    fun createBot(name: String, token: String): LinkFixerBot {
        return LinkFixerBot(
            botToken = token,
            botName = name,
            commandHandlerService = commandHandlerService,
            mediaHandlerService = mediaHandlerService,
            messageProcessorService = messageProcessorService,
            chatSettingsManagementService = chatSettingsManagementService
        )
    }
}