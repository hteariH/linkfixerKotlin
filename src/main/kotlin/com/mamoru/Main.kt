package com.mamoru

import com.mamoru.repository.ChatJpaRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@EnableScheduling
@SpringBootApplication
class LinkFixerBotApplication {


    @Bean
    fun telegramBotsApi(): TelegramBotsApi {
        return TelegramBotsApi(DefaultBotSession::class.java)
    }


    @Bean
    fun registerBot(telegramBotsApi: TelegramBotsApi, linkFixerBot: LinkFixerBot, chatJpaRepository: ChatJpaRepository): LinkFixerBot {
        try {
            telegramBotsApi.registerBot(linkFixerBot)
            println("Bot started successfully!")
//            println(chatJpaRepository.findAll());
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
        return linkFixerBot
    }
}

fun main(args: Array<String>) {
    runApplication<LinkFixerBotApplication>(*args)
}