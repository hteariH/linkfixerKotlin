package com.mamoru.factory

import com.mamoru.GrokBot
import com.mamoru.service.AiService
import com.mamoru.service.ChatSettingsService
import org.springframework.stereotype.Component

@Component
class TelegramBotFactory(
    private val aiService: AiService,
    private val chatSettingsService: ChatSettingsService
) {
    fun createBot(name: String, token: String): GrokBot {
        return GrokBot(
            botToken = token,
            botName = name,
            aiService = aiService,
            chatSettingsService = chatSettingsService
        )
    }
}
