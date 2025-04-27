package com.mamoru.service

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.mamoru.entity.ChatSettings
import com.mamoru.util.Constants
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import java.net.URL
import java.util.Base64
import org.slf4j.LoggerFactory

/**
 * Service for interacting with Google's Gemini AI API
 * Handles generating jokes and picture comments
 */
@Service
class GeminiAIService(
    private val chatSettingsManagementService: ChatSettingsManagementService
) {
    private val logger = LoggerFactory.getLogger(GeminiAIService::class.java)
    private val client = Client()
    private val defaultModel = Constants.AI.DEFAULT_MODEL

    /**
     * Generates a random joke using Gemini AI
     * Uses the custom joke prompt from chat settings if available
     *
     * @param chatId The chat ID to get custom prompt for
     * @return The generated joke text
     */
    fun getRandomJoke(chatId: Long? = null): String {
        try {
            // Get the joke prompt from chat settings if chatId is provided
            val prompt = if (chatId != null) {
                chatSettingsManagementService.getChatSettings(chatId).jokePrompt
            } else {
                Constants.AI.DEFAULT_JOKE_PROMPT
            }

            val response = client.models.generateContent(
                defaultModel,
                prompt,
                null
            )

            return response.text() ?: Constants.AI.DEFAULT_JOKE_FAILURE_MESSAGE
        } catch (e: Exception) {
            logger.error("Error generating joke: ${e.message}", e)
            return Constants.AI.DEFAULT_JOKE_FAILURE_MESSAGE
        }
    }

    /**
     * Generates a comment for a picture using Gemini AI
     * Uses the custom picture prompt from chat settings
     *
     * @param photoSize The Telegram photo object
     * @param chatId The chat ID to get custom prompt for
     * @param bot The Telegram bot instance (needed to download the photo)
     * @param botToken The Telegram bot token
     * @return The generated picture comment
     */
    fun generatePictureComment(photoSize: PhotoSize, chatId: Long, bot: TelegramLongPollingBot, botToken: String): String {
        try {
            // Get the file from Telegram
            val getFile = GetFile()
            getFile.fileId = photoSize.fileId
            val file = bot.execute(getFile)

            // Download the file
            val fileUrl = "https://api.telegram.org/file/bot${botToken}/${file.filePath}"
            val imageBytes = URL(fileUrl).readBytes()

            // Get the picture prompt from chat settings
            val picturePrompt = chatSettingsManagementService.getChatSettings(chatId).picturePrompt

            val content = Content.fromParts(
                Part.fromText(picturePrompt),
                Part.fromText(Constants.AI.PICTURE_ANALYSIS_INSTRUCTION),
                Part.fromBytes(imageBytes, "image/jpeg")
            )

            // Send the request to Gemini
            val response = client.models.generateContent(
                defaultModel,
                content,
                null
            )

            return response.text() ?: Constants.AI.DEFAULT_PICTURE_FAILURE_MESSAGE
        } catch (e: Exception) {
            logger.error("Error generating picture comment: ${e.message}", e)
            return "${Constants.AI.DEFAULT_PICTURE_FAILURE_MESSAGE}: ${e.message}"
        }
    }

}
