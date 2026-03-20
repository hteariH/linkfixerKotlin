package com.mamoru.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
class TelegramBotConfig {
    var name: String = ""
    var token: String = ""
}
