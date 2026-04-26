package com.mamoru

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.mamoru.config.ManagedUpdateDeserializer
import com.mamoru.config.TelegramBotConfig
import com.mamoru.factory.TelegramBotFactory
import com.mamoru.service.ManagedBotService
import com.mamoru.service.PrimaryBotHolder
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.api.objects.message.Message

@EnableScheduling
@EnableConfigurationProperties(TelegramBotConfig::class)
@SpringBootApplication
class HydraManagerBotApplication {

    private val logger = LoggerFactory.getLogger(HydraManagerBotApplication::class.java)

    @Bean
    fun registerBot(
        botsApplication: TelegramBotsLongPollingApplication,
        telegramBotConfig: TelegramBotConfig,
        telegramBotFactory: TelegramBotFactory,
        managedBotService: ManagedBotService,
        primaryBotHolder: PrimaryBotHolder
    ): HydraManagerBot {
        val bot = telegramBotFactory.createBot(telegramBotConfig.name, telegramBotConfig.token)

        // Register client and name for managed bots to delegate invoice sending
        primaryBotHolder.client = bot.telegramClient
        primaryBotHolder.botName = telegramBotConfig.name

        try {
            botsApplication.registerBot(telegramBotConfig.token, bot)
            patchBotsApplicationMapper(botsApplication, managedBotService)
            println("Bot ${telegramBotConfig.name} started successfully!")
        } catch (e: Exception) {
            println("Failed to start bot ${telegramBotConfig.name}: ${e.message}")
            e.printStackTrace()
        }

        return bot
    }

    /**
     * Attempts to patch the ObjectMapper inside TelegramBotsLongPollingApplication
     * to intercept managed_bot updates (non-standard Telegram Bot API field).
     * If patching fails, managed bots can still be activated manually via /activateBot.
     */
    private fun patchBotsApplicationMapper(botsApplication: TelegramBotsLongPollingApplication, managedBotService: ManagedBotService) {
        try {
            val mapperField = generateSequence<Class<*>>(botsApplication::class.java) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { it.type.name.endsWith(".ObjectMapper") }
                ?: run { logger.warn("Could not find ObjectMapper in TelegramBotsLongPollingApplication — managed_bot auto-activation disabled"); return }

            mapperField.isAccessible = true
            val mapper = (if (java.lang.reflect.Modifier.isStatic(mapperField.modifiers))
                mapperField.get(null) else mapperField.get(botsApplication))
                ?: run { logger.warn("ObjectMapper field is null — managed_bot auto-activation disabled"); return }

            // Use reflection to register the module to handle both Jackson 2 and Jackson 3 types if necessary
            // In our case, we know it's Jackson 2 because we forced it in build.gradle.kts
            val registerModuleMethod = mapper::class.java.methods.firstOrNull { it.name == "registerModule" }
            if (registerModuleMethod != null) {
                registerModuleMethod.invoke(mapper, SimpleModule().addDeserializer(Message::class.java, ManagedUpdateDeserializer(managedBotService)))
                logger.info("Successfully patched TelegramBotsLongPollingApplication ObjectMapper for managed_bot support")
            } else {
                logger.warn("Could not find registerModule method on ObjectMapper — managed_bot auto-activation disabled")
            }
        } catch (e: Exception) {
            logger.warn("Could not patch ObjectMapper: ${e.message} — use /activateBot for manual bot activation")
        }
    }
}

fun main(args: Array<String>) {
    runApplication<HydraManagerBotApplication>(*args)
}
