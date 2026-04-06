package com.mamoru.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@Service
class MessageAnalyzerService {
    private val logger = LoggerFactory.getLogger(MessageAnalyzerService::class.java)
    private val dataDir = "/data"
    private val usernameMapFile = File("$dataDir/username_map.txt")

    // username (lowercase, no @) -> userId
    private val usernameToUserId: MutableMap<String, Long> = loadUsernameMap()

    // usernames (lowercase) of all registered managed bots — messages mentioning them are not saved
    private val managedBotUsernames = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun registerManagedBot(username: String) {
        managedBotUsernames.add(username.lowercase())
        logger.debug("Registered managed bot username for filter: @{}", username)
    }

    fun analyzeMessageIfFromTargetUser(message: Message): Boolean {
        if (!message.hasText()) return false
        val userId = message.from?.id ?: return false
        val username = message.from?.userName
        val textLower = message.text.lowercase()

        if (textLower.contains("@hydramanager_bot")) return false
        if (managedBotUsernames.any { textLower.contains("@$it") }) {
            logger.debug("Skipping message save — mentions a managed bot")
            return false
        }

        // Keep username -> userId mapping up to date
        if (username != null) {
            val key = username.lowercase()
            if (!usernameToUserId.containsKey(key)) {
                usernameToUserId[key] = userId
                persistUsernameMapping(key, userId)
                logger.info("Mapped username @$username to userId $userId")
            }
        }

        try {
            saveMessageToFile(message.text, userId)
            return true
        } catch (e: Exception) {
            logger.error("Error saving message from user $userId: ${e.message}", e)
            return false
        }
    }

    /** Resolves a Telegram username (with or without @) to a stored userId, or null if unknown. */
    fun resolveUserId(username: String): Long? =
        usernameToUserId[username.removePrefix("@").lowercase()]

    fun readSavedMessages(userId: Long): String? {
        return try {
            val file = File("$dataDir/$userId.txt")
            if (!file.exists()) {
                logger.warn("No message file for userId $userId")
                return null
            }
            val content = file.readText()
            val maxLength = 150_000
            if (content.length > maxLength) content.substring(content.length - maxLength) else content
        } catch (e: Exception) {
            logger.error("Error reading messages for userId $userId: ${e.message}", e)
            null
        }
    }

    private fun saveMessageToFile(text: String, userId: Long) {
        val dir = File(dataDir)
        if (!dir.exists()) dir.mkdirs()

        val entry = "\n$text\n----------------------------------------"
        Files.write(
            Paths.get("$dataDir/$userId.txt"),
            entry.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    private fun loadUsernameMap(): MutableMap<String, Long> {
        if (!usernameMapFile.exists()) return mutableMapOf()
        return usernameMapFile.readLines()
            .mapNotNull { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) parts[0] to parts[1].toLongOrNull() else null
            }
            .filter { (_, id) -> id != null }
            .associate { (name, id) -> name to id!! }
            .toMutableMap()
    }

    private fun persistUsernameMapping(username: String, userId: Long) {
        try {
            val dir = File(dataDir)
            if (!dir.exists()) dir.mkdirs()
            Files.write(
                usernameMapFile.toPath(),
                "$username:$userId\n".toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        } catch (e: Exception) {
            logger.error("Failed to persist username mapping: ${e.message}", e)
        }
    }
}
