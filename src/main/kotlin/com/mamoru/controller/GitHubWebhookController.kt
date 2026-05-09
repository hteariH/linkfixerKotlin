package com.mamoru.controller

import com.mamoru.service.GitHubDispatchService
import com.mamoru.service.PrimaryBotHolder
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@RestController
@RequestMapping("/api/github")
class GitHubWebhookController(
    private val primaryBotHolder: PrimaryBotHolder,
    private val gitHubDispatchService: GitHubDispatchService
) {
    private val logger = LoggerFactory.getLogger(GitHubWebhookController::class.java)

    @PostMapping("/webhook")
    fun handleGitHubNotification(@RequestBody payload: Map<String, Any>): ResponseEntity<String> {
        logger.info("Received notification from GitHub: $payload")

        val chatId = payload["chat_id"]?.toString()?.toLongOrNull()
        var messageText = payload["message"]?.toString() ?: "GitHub Action completed"

        if (chatId != null) {
            val client = primaryBotHolder.client
            if (client != null) {
                try {
                    val prUrl = gitHubDispatchService.getLatestPullRequest()
                    if (prUrl != null) {
                        messageText += "\n\nПоследний PR: $prUrl"
                    }
                    
                    val sendMessage = SendMessage(chatId.toString(), messageText)
                    client.execute(sendMessage)
                    logger.info("Sent GitHub notification to chat $chatId")
                } catch (e: Exception) {
                    logger.error("Failed to send notification to Telegram", e)
                }
            } else {
                logger.warn("Primary bot client is not initialized")
            }
        } else {
            logger.warn("chat_id not found in GitHub notification payload")
        }

        return ResponseEntity.ok("Notification processed")
    }
}
