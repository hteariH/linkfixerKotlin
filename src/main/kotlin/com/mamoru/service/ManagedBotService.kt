package com.mamoru.service

import com.mamoru.HydraManagerBot
import com.mamoru.config.TelegramBotConfig
import com.mamoru.entity.ManagedBot
import com.mamoru.factory.TelegramBotFactory
import com.mamoru.repository.ManagedBotRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.concurrent.ConcurrentHashMap

data class PendingCreation(val targetUsername: String, val chatId: Long)

@Service
class ManagedBotService(
    private val managedBotRepository: ManagedBotRepository,
    private val telegramBotFactory: TelegramBotFactory,
    private val telegramBotsApi: TelegramBotsApi,
    private val botConfig: TelegramBotConfig,
    private val messageAnalyzerService: MessageAnalyzerService,
    private val botRegistryService: BotRegistryService,
    @Lazy private val mainBot: HydraManagerBot
) {
    private val logger = LoggerFactory.getLogger(ManagedBotService::class.java)
    private val restTemplate = RestTemplate()
    private val apiBase = "https://api.telegram.org/bot"

    // suggestedUsername (lowercase) -> pending creation info
    private val pendingCreations = ConcurrentHashMap<String, PendingCreation>()

    fun generateCreationLink(suggestedBotUsername: String): String =
        "https://t.me/newbot/${botConfig.name}/$suggestedBotUsername?name=$suggestedBotUsername"

    fun storePendingCreation(suggestedUsername: String, targetUsername: String, chatId: Long) {
        pendingCreations[suggestedUsername.lowercase()] = PendingCreation(targetUsername, chatId)
        logger.info("Stored pending creation: @$suggestedUsername -> $targetUsername (chatId=$chatId)")
    }

    /** Called automatically when a managed_bot update is received. */
    fun handleManagedBotCreated(botId: Long, botUsername: String) {
        val pending = pendingCreations.remove(botUsername.lowercase())
        if (pending == null) {
            logger.warn("Received managed_bot update for @$botUsername but no pending creation found")
            return
        }

        val result = activateManagedBotInternal(botUsername, botId, pending.targetUsername)
        mainBot.sendMessageToChat(pending.chatId, result)
    }

    fun activateManagedBot(botUsername: String, targetUsername: String, botId: Long? = null): String {
        if (managedBotRepository.findByBotUsername(botUsername) != null) {
            return "Bot @$botUsername is already registered."
        }

        val resolvedBotId = botId ?: tryGetChatId(botUsername)
            ?: return "Could not resolve @$botUsername — Telegram returned 'chat not found'.\n" +
                "Provide the bot's numeric ID directly:\n" +
                "/activateBot $botUsername $targetUsername <botId>"

        return activateManagedBotInternal(botUsername, resolvedBotId, targetUsername)
    }

    private fun activateManagedBotInternal(botUsername: String, botId: Long, targetUsername: String): String {
        if (managedBotRepository.findByBotUsername(botUsername) != null) {
            return "Bot @$botUsername is already registered."
        }

        val cleanTarget = targetUsername.removePrefix("@")
        val targetUserId = cleanTarget.toLongOrNull()
            ?: messageAnalyzerService.resolveUserId(cleanTarget)
            ?: return "No messages found for @$cleanTarget. " +
                "They need to send at least one message in a chat where this bot is active first."

        return try {
            val managedBot = managedBotRepository.save(
                ManagedBot(botUsername = botUsername, botId = botId, targetUserId = targetUserId)
            )
            registerBotInstance(managedBot)
            "Bot @$botUsername is now active and will impersonate $targetUsername"
        } catch (e: Exception) {
            logger.error("Failed to activate managed bot $botUsername: ${e.message}", e)
            "Failed to activate @$botUsername: ${e.message}"
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    @Order(2)
    fun registerAllFromDb() {
        botRegistryService.registerBot(botConfig.name) // Register main bot
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
        val token = fetchManagedBotToken(managedBot.botId)
        val bot = telegramBotFactory.createBot(managedBot.botUsername, token, managedBot.targetUserId)
        try {
            telegramBotsApi.registerBot(bot)
            messageAnalyzerService.registerManagedBot(managedBot.botUsername)
            botRegistryService.registerBot(managedBot.botUsername)
            logger.info("Registered managed bot @${managedBot.botUsername} (botId=${managedBot.botId}) for userId ${managedBot.targetUserId}")
        } catch (e: TelegramApiException) {
            logger.error("Failed to register managed bot ${managedBot.botUsername}: ${e.message}", e)
            throw e
        }
    }

    private fun tryGetChatId(username: String): Long? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val result = post("getChat", mapOf("chat_id" to "@$username")) as Map<String, Any>
            (result["id"] as Number).toLong()
        } catch (e: Exception) {
            logger.warn("getChat failed for @$username: ${e.message}")
            null
        }
    }

    private fun fetchManagedBotToken(botId: Long): String {
        return post("getManagedBotToken", mapOf("user_id" to botId)) as String
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
