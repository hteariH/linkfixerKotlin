package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.charset.StandardCharsets

/**
 * Service for analyzing messages from a specific user using Gemini AI
 * and saving the messages to a file
 */
@Service
class MessageAnalyzerService() {
    private val logger = LoggerFactory.getLogger(MessageAnalyzerService::class.java)
//    private val targetUserId = 123616664L
    private val targetUserId = 426020724L
    private val outputFilePath = "/data/"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Analyzes a message if it's from the target user and saves it to a file
     * 
     * @param message The Telegram message to analyze
     * @return true if the message was saved, false otherwise
     */
    fun analyzeMessageIfFromTargetUser(message: Message): Boolean {
        if (!message.hasText()) return false

        val userId = message.from?.id ?: return false

        // Check if the message is from the target user
//        if (userId != targetUserId) return false

        logger.info("Saving message from user (ID: $userId)")
        if (message.text.contains("@ChatManagerAssistantBot")) return false
        try {
            // Save the message to file
            saveMessageToFile(message.text, userId)

            logger.info("Successfully saved message from target user")
            return true
        } catch (e: Exception) {
            logger.error("Error saving message from target user: ${e.message}", e)
            return false
        }
    }

    /**
     * Saves the original message to the output file
     * 
     * @param originalMessage The original message text
     */
    private fun saveMessageToFile(originalMessage: String, userId: Long) {
        try {
            // Create directory if it doesn't exist
            val directory = File("/data")
            if (!directory.exists()) {
                directory.mkdirs()
                logger.info("Created directory: /data")
            }

            // Create file if it doesn't exist
            val file = File("$outputFilePath$userId.txt")
            if (!file.exists()) {
                file.createNewFile()
                logger.info("Created file: ${file.absolutePath}")
            }

            // Format the entry with timestamp
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val entry = """
                |
                |$originalMessage
                |
                |----------------------------------------
            """.trimMargin()

            // Append to file
            Files.write(
                Paths.get("$outputFilePath$userId.txt"),
                entry.toByteArray(),
                StandardOpenOption.APPEND
            )

            logger.info("Successfully saved message to $outputFilePath$userId.txt")
        } catch (e: Exception) {
            logger.error("Error saving message to file: ${e.message}", e)
        }
    }

    /**
     * Reads the saved messages from the output file
     * 
     * @return The content of the file as a string, or null if the file doesn't exist or can't be read
     */
    fun readSavedMessages(): String? {
        try {
            val file = File("$outputFilePath$targetUserId.txt")
            if (!file.exists()) {
                logger.warn("File does not exist: $outputFilePath$targetUserId.txt")
                return null
            }

            return Files.readString(Paths.get("$outputFilePath$targetUserId.txt"), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Error reading saved messages: ${e.message}", e)
            return null
        }
    }
    fun readSavedMessages(userId: Long): String? {
        try {
            val file = File("$outputFilePath$userId.txt")
            if (!file.exists()) {
                logger.warn("File does not exist: $outputFilePath$userId.txt")
                return null
            }

            return Files.readString(Paths.get("$outputFilePath$userId.txt"), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Error reading saved messages: ${e.message}", e)
            return null
        }
    }

}
