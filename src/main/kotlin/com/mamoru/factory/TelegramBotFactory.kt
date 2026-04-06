package com.mamoru.factory

import com.mamoru.HydraManagerBot
import com.mamoru.service.ChatSettingsManagementService
import com.mamoru.service.CommandHandlerService
import com.mamoru.service.MessageAnalyzerService
import com.mamoru.service.MessageCacheService
import com.mamoru.service.MessageProcessorService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions

@Component
class TelegramBotFactory(
    private val commandHandlerService: CommandHandlerService,
    private val messageProcessorService: MessageProcessorService,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val messageAnalyzerService: MessageAnalyzerService,
    private val messageCacheService: MessageCacheService
) {

    fun createBot(
        name: String,
        token: String,
        targetUserId: Long? = null,
        botOptions: DefaultBotOptions = DefaultBotOptions()
    ): HydraManagerBot {
        return HydraManagerBot(
            botToken = token,
            botName = name,
            commandHandlerService = commandHandlerService,
            messageProcessorService = messageProcessorService,
            chatSettingsManagementService = chatSettingsManagementService,
            messageAnalyzerService = messageAnalyzerService,
            messageCacheService = messageCacheService,
            targetUserId = targetUserId,
            botOptions = botOptions
        )
    }
}
