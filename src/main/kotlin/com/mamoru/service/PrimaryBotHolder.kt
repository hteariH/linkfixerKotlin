package com.mamoru.service

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.generics.TelegramClient

/**
 * Holds a reference to the primary HydraManagerBot's TelegramClient so that
 * managed bots can delegate invoice sending to it.
 * Populated by Main.kt after the primary bot is registered.
 */
@Component
class PrimaryBotHolder {
    @Volatile var client: TelegramClient? = null
    @Volatile var botName: String? = null
}
