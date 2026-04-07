package com.mamoru

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.mamoru.config.ManagedUpdateDeserializer
import com.mamoru.config.TelegramBotConfig
import com.mamoru.factory.TelegramBotFactory
import com.mamoru.service.ManagedBotService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@EnableScheduling
@EnableConfigurationProperties(TelegramBotConfig::class)
@SpringBootApplication
class HydraManagerBotApplication {

    private val logger = LoggerFactory.getLogger(HydraManagerBotApplication::class.java)

    @Bean
    fun telegramBotsApi(): TelegramBotsApi {
        return TelegramBotsApi(DefaultBotSession::class.java)
    }

    @Bean
    fun registerBot(
        telegramBotsApi: TelegramBotsApi,
        telegramBotConfig: TelegramBotConfig,
        telegramBotFactory: TelegramBotFactory,
        managedBotService: ManagedBotService
    ): HydraManagerBot {
        val options = DefaultBotOptions().apply {
            allowedUpdates = listOf(
                "message", "edited_message", "channel_post", "edited_channel_post",
                "inline_query", "chosen_inline_result", "callback_query",
                "shipping_query", "pre_checkout_query", "poll", "poll_answer",
                "my_chat_member", "chat_member", "chat_join_request",
                "managed_bot"
            )
        }
        val bot = telegramBotFactory.createBot(telegramBotConfig.name, telegramBotConfig.token, botOptions = options)
        try {
            val session = telegramBotsApi.registerBot(bot)
            patchSessionMapper(session, managedBotService)
            println("Bot ${telegramBotConfig.name} started successfully!")
        } catch (e: TelegramApiException) {
            println("Failed to start bot ${telegramBotConfig.name}: ${e.message}")
            e.printStackTrace()
        }

        return bot
    }

    /**
     * Patches the ObjectMapper used by DefaultBotSession to intercept managed_bot updates,
     * which the telegrambots library otherwise silently discards as unknown fields.
     */
    private fun patchSessionMapper(session: Any, managedBotService: ManagedBotService) {
        try {
            val targetClass = if (session is DefaultBotSession) session::class.java
                              else DefaultBotSession::class.java

            val mapperField = generateSequence<Class<*>>(targetClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { ObjectMapper::class.java.isAssignableFrom(it.type) }
                ?: run { logger.warn("Could not find ObjectMapper field in DefaultBotSession — managed_bot auto-activation disabled"); return }

            mapperField.isAccessible = true
            val mapper = (if (java.lang.reflect.Modifier.isStatic(mapperField.modifiers))
                mapperField.get(null) else mapperField.get(session)) as? ObjectMapper
                ?: run { logger.warn("ObjectMapper field is null — managed_bot auto-activation disabled"); return }

            mapper.registerModule(
                SimpleModule().addDeserializer(Message::class.java, ManagedUpdateDeserializer(managedBotService))
            )
            logger.info("Successfully patched DefaultBotSession ObjectMapper for managed_bot support")
        } catch (e: Exception) {
            logger.warn("Could not patch DefaultBotSession ObjectMapper: ${e.message} — managed_bot auto-activation disabled")
        }
    }
}

fun main(args: Array<String>) {
    runApplication<HydraManagerBotApplication>(*args)
}
