package com.mamoru

import com.mamoru.config.TelegramBotConfig
import com.mamoru.factory.TelegramBotFactory
import com.mamoru.service.PrimaryBotHolder
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

@EnableScheduling
@EnableConfigurationProperties(TelegramBotConfig::class)
@SpringBootApplication(scanBasePackages = ["com.mamoru"])
class HydraManagerBotApplication {

    private val logger = LoggerFactory.getLogger(HydraManagerBotApplication::class.java)

    @Bean
    fun registerBot(
        botsApplication: TelegramBotsLongPollingApplication,
        telegramBotConfig: TelegramBotConfig,
        telegramBotFactory: TelegramBotFactory,
        primaryBotHolder: PrimaryBotHolder
    ): HydraManagerBot {
        val bot = telegramBotFactory.createBot(
            telegramBotConfig.name,
            telegramBotConfig.token,
            includeManagedBotService = true
        )

        // Register client and name for managed bots to delegate invoice sending
        primaryBotHolder.client = bot.telegramClient
        primaryBotHolder.botName = telegramBotConfig.name

        try {
            botsApplication.registerBot(telegramBotConfig.token, bot)
            logger.info("Bot ${telegramBotConfig.name} started successfully!")
        } catch (e: Exception) {
            logger.error("Failed to start bot ${telegramBotConfig.name}: ${e.message}", e)
        }

        return bot
    }
}

fun main(args: Array<String>) {
    runApplication<HydraManagerBotApplication>(*args)
}
