package com.mamoru.factory

import com.mamoru.HydraManagerBot
import com.mamoru.service.ChatSettingsManagementService
import com.mamoru.service.CommandHandlerService
import com.mamoru.service.MessageAnalyzerService
import com.mamoru.service.MessageProcessorService
import org.springframework.stereotype.Component

@Component
class TelegramBotFactory(
    private val commandHandlerService: CommandHandlerService,
    private val messageProcessorService: MessageProcessorService,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val messageAnalyzerService: MessageAnalyzerService
) {

    fun createBot(name: String, token: String, targetUserId: Long? = null): HydraManagerBot {
        return HydraManagerBot(
            botToken = token,
            botName = name,
            commandHandlerService = commandHandlerService,
            messageProcessorService = messageProcessorService,
            chatSettingsManagementService = chatSettingsManagementService,
            messageAnalyzerService = messageAnalyzerService,
            targetUserId = targetUserId
        )
    }
}
