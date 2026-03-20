package com.mamoru

import com.mamoru.config.TelegramBotConfig
import com.mamoru.factory.TelegramBotFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@EnableScheduling
@EnableConfigurationProperties(TelegramBotConfig::class)
@SpringBootApplication
class GrokBotApplication {

    @Bean
    fun telegramBotsApi(): TelegramBotsApi {
        return TelegramBotsApi(DefaultBotSession::class.java)
    }

    @Bean
    fun registerBot(
        telegramBotsApi: TelegramBotsApi,
        telegramBotConfig: TelegramBotConfig,
        telegramBotFactory: TelegramBotFactory
    ): GrokBot {
        return try {
            val bot = telegramBotFactory.createBot(telegramBotConfig.name, telegramBotConfig.token)
            telegramBotsApi.registerBot(bot)
            println("Bot ${telegramBotConfig.name} started successfully!")
            bot
        } catch (e: TelegramApiException) {
            throw RuntimeException("Failed to start bot ${telegramBotConfig.name}: ${e.message}", e)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<GrokBotApplication>(*args)
}
