package com.mamoru.service

import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot

/**
 * Holds a reference to the primary HydraManagerBot instance so that
 * managed bots can delegate invoice sending to it.
 * Populated by Main.kt after the primary bot is registered.
 */
@Component
class PrimaryBotHolder {
    @Volatile var bot: TelegramLongPollingBot? = null
    @Volatile var botName: String? = null
}
