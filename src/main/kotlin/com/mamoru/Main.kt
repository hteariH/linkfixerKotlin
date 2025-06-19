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
class LinkFixerBotApplication {


    @Bean
    fun telegramBotsApi(): TelegramBotsApi {
        return TelegramBotsApi(DefaultBotSession::class.java)
    }


    @Bean
    fun registerBots(
        telegramBotsApi: TelegramBotsApi, 
        telegramBotConfig: TelegramBotConfig,
        telegramBotFactory: TelegramBotFactory,
    ): List<LinkFixerBot> {
        val registeredBots = mutableListOf<LinkFixerBot>()

        for (botConfig in telegramBotConfig.bots) {
            try {
                val bot = telegramBotFactory.createBot(botConfig.name, botConfig.token)
                telegramBotsApi.registerBot(bot)
                registeredBots.add(bot)
                println("Bot ${botConfig.name} started successfully!")
            } catch (e: TelegramApiException) {
                println("Failed to start bot ${botConfig.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        return registeredBots
    }
}

fun main(args: Array<String>) {
    runApplication<LinkFixerBotApplication>(*args)
}
