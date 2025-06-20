package com.mamoru.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Configuration class for Telegram bots
 */
@ConfigurationProperties(prefix = "telegram")
class TelegramBotConfig {
    var bots: List<BotConfig> = ArrayList()

    class BotConfig {
        var name: String = ""
        var token: String = ""
    }
}
