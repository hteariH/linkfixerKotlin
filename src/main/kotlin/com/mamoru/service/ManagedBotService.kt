package com.mamoru.service

import com.mamoru.config.TelegramBotConfig
import com.mamoru.entity.ManagedBot
import com.mamoru.factory.TelegramBotFactory
import com.mamoru.repository.ManagedBotRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

@Service
class ManagedBotService(
    private val managedBotRepository: ManagedBotRepository,
    private val telegramBotFactory: TelegramBotFactory,
    private val telegramBotsApi: TelegramBotsApi,
    private val botConfig: TelegramBotConfig,
    private val messageAnalyzerService: MessageAnalyzerService
) {
    private val logger = LoggerFactory.getLogger(ManagedBotService::class.java)
    private val restTemplate = RestTemplate()
    private val apiBase = "https://api.telegram.org/bot"

    fun generateCreationLink(suggestedBotUsername: String): String =
        "https://t.me/newbot/${botConfig.name}/$suggestedBotUsername?name=$suggestedBotUsername"

    fun activateManagedBot(botUsername: String, targetUsername: String): String {
        if (managedBotRepository.findByBotUsername(botUsername) != null) {
            return "Bot @$botUsername is already registered."
        }

        val cleanTarget = targetUsername.removePrefix("@")
        val targetUserId = cleanTarget.toLongOrNull()
            ?: messageAnalyzerService.resolveUserId(cleanTarget)
            ?: return "No messages found for @$cleanTarget. " +
                "They need to send at least one message in a chat where this bot is active first."

        return try {
            val botId = getChatId(botUsername)
            val token = fetchManagedBotToken(botId)
            val managedBot = managedBotRepository.save(
                ManagedBot(botUsername = botUsername, botToken = token, targetUserId = targetUserId)
            )
            registerBotInstance(managedBot)
            "Bot @$botUsername is now active and will impersonate $targetUsername"
        } catch (e: Exception) {
            logger.error("Failed to activate managed bot $botUsername: ${e.message}", e)
            "Failed to activate @$botUsername: ${e.message}"
        }
    }

    fun registerAllFromDb() {
        val bots = managedBotRepository.findAll()
        logger.info("Registering ${bots.size} managed bot(s) from database")
        for (bot in bots) {
            try {
                registerBotInstance(bot)
            } catch (e: Exception) {
                logger.error("Failed to register managed bot ${bot.botUsername}: ${e.message}", e)
            }
        }
    }

    private fun registerBotInstance(managedBot: ManagedBot) {
        val bot = telegramBotFactory.createBot(managedBot.botUsername, managedBot.botToken, managedBot.targetUserId)
        try {
            telegramBotsApi.registerBot(bot)
            logger.info("Registered managed bot @${managedBot.botUsername} for userId ${managedBot.targetUserId}")
        } catch (e: TelegramApiException) {
            logger.error("Failed to register managed bot ${managedBot.botUsername}: ${e.message}", e)
            throw e
        }
    }

    private fun getChatId(username: String): Long {
        @Suppress("UNCHECKED_CAST")
        val result = post("getChat", mapOf("chat_id" to "@$username")) as Map<String, Any>
        return (result["id"] as Number).toLong()
    }

    private fun fetchManagedBotToken(botId: Long): String {
        return post("getManagedBotToken", mapOf("bot_user_id" to botId)) as String
    }

    private fun post(method: String, params: Map<String, Any>): Any {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = restTemplate.postForObject(
            "$apiBase${botConfig.token}/$method",
            HttpEntity(params, headers),
            Map::class.java
        ) ?: throw RuntimeException("No response from Telegram API for $method")

        if (response["ok"] != true) {
            throw RuntimeException("Telegram API error [$method]: ${response["description"]}")
        }
        return response["result"] ?: throw RuntimeException("No result in response for $method")
    }
}
