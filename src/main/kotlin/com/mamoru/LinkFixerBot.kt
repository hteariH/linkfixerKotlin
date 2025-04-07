package com.mamoru

import com.mamoru.repository.ChatRepository
import com.mamoru.service.ChatSettingsManagementService
import com.mamoru.service.TikTokDownloaderService
import com.mamoru.service.VideoCacheService
import com.mamoru.service.url.ProcessedText
import com.mamoru.service.url.ProcessedUrl
import com.mamoru.service.url.UrlProcessingPipeline
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern


// Other imports as needed

@Component
class LinkFixerBot(
    @Value("\${telegram.bot.token}") private val botToken: String,
    private val videoCacheService: VideoCacheService,
    private val tikTokDownloaderService: TikTokDownloaderService,
    private val chatRepository: ChatRepository,
    private val chatService: ChatSettingsManagementService,
    private val urlProcessingPipeline: UrlProcessingPipeline
) : TelegramLongPollingBot(botToken) {

    override fun getBotUsername(): String {
        return "LinkFixerBot" // Replace with your actual bot username
    }

    private fun shouldSendCounter(chatId: Long): Boolean {
        return chatService.getChatSettings(chatId).sendCounterUntilWin
    }


    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return
        val message = update.message
        val chatIdL = message.chatId
        val chatId = chatIdL.toString()
        val text = message.text
        if (message.replyToMessage != null && message.chatId == 123616664L) {
            handleReplyToForwardedMessage(message);
            return
        }

        if (message.text.startsWith("/togglecounter", ignoreCase = true)) {
            val currentSettings = chatService.getChatSettings(chatIdL)
            val newSetting = !currentSettings.sendCounterUntilWin
            chatService.updateSendCounterUntilWin(chatIdL, newSetting)

            val responseText = if (newSetting) {
                "Counter until win will now be shown in this chat"
            } else {
                "Counter until win will not be shown in this chat"
            }

            sendMessageToChat(chatIdL, responseText)
        } else if (message.text.startsWith("/togglejoke", ignoreCase = true)) {
            val currentSettings = chatService.getChatSettings(chatIdL)
            val newSetting = !currentSettings.sendRandomJoke
            chatService.updateSendJoke(chatIdL, newSetting)

            val responseText = if (newSetting) {
                "Counter until win will now be shown in this chat"
            } else {
                "Counter until win will not be shown in this chat"
            }
        }

        val processedText = urlProcessingPipeline.processTextAndReplace(text)

        if (processedText.processedUrls.isNotEmpty()) {
            handleProcessedUrls(message, processedText)
        }

        sendMessageToChat(
            123616664L,
            chatId + ": " + message.from.userName + ": " + message.text + ": " + message.messageId
        )

        chatService.addChat(message.chatId);

    }

    private fun handleProcessedUrls(message: Message, processedText: ProcessedText) {
        val processedUrls = processedText.processedUrls
        for (processedUrl in processedUrls) {
            when (processedUrl.type) {
                "tiktok" -> handleTikTokUrl(message, processedUrl.original)
                "twitter", "instagram" -> {
                    if (processedUrl.original != processedUrl.converted) {
                        sendMessageToChat(message.chatId, processedText.modifiedText)
                        return
                    }
                }
            }
        }
    }

    private fun handleTikTokUrl(message: Message, url: String) {
        try {
            // Check if video is already in cache
            val cachedVideo = videoCacheService.getCachedVideo(url)
            if (cachedVideo != null) {
                sendVideoToChat(message, cachedVideo)
                return
            }

            // Download video if not cached
            val downloadedFile = tikTokDownloaderService.downloadVideo(url)
            if (downloadedFile != null) {
                // Cache the video for future use
                videoCacheService.cacheVideo(url, downloadedFile)
                sendVideoToChat(message, downloadedFile)
            }
        } catch (e: Exception) {
            sendMessageToChat(message.chatId, "Failed to process TikTok video: ${e.message}")
        }
    }

    private fun sendVideoToChat(message: Message, cachedVideo: File) {
        if (cachedVideo != null) {
            val sendVideo = SendVideo.builder()
                .chatId(message.chatId)
                .video(InputFile(cachedVideo))
                .caption(message.from.userName + " sent:" + message.text)
                .build()

            execute(sendVideo)
            println("Sent video to chat: ${message.chatId}")
        } else {
            println("Failed to download video ")
        }
    }




    private fun handleReplyToForwardedMessage(replyMessage: Message) {
        val originalForwardedText: String = replyMessage.replyToMessage.getText() ?: ""
        val split = originalForwardedText.split(":")


        try {
            // Extract information from the forwarded message
            val originalChatId: Long = split.first().trim().toLong()
            val originalMessageId: Int = split.last().trim().toInt()
            val replyText: String = replyMessage?.getText() ?: ""


            // Create reply message
            val sendMessage = SendMessage()
            sendMessage.setChatId(originalChatId)
            sendMessage.text = replyText
            sendMessage.replyToMessageId = originalMessageId


            // Send the reply
            execute(sendMessage)


            // Confirm to admin that reply was sent
            val confirmMessage = SendMessage()
            confirmMessage.setChatId(replyMessage.chatId)
            confirmMessage.text = "Reply sent to chat $originalChatId"
            execute(confirmMessage)
        } catch (e: NumberFormatException) {
            // Handle exceptions
            try {
                execute<Message, SendMessage>(
                    SendMessage(
                        replyMessage.getChatId().toString(),
                        "Failed to send reply: " + e.message
                    )
                )
            } catch (ex: TelegramApiException) {
                // Log the error
            }
        } catch (e: TelegramApiException) {
            try {
                execute<Message, SendMessage>(
                    SendMessage(
                        replyMessage.getChatId().toString(),
                        "Failed to send reply: " + e.message
                    )
                )
            } catch (ex: TelegramApiException) {
            }
        }
    }

    fun sendMessageToChat(chatId: Long, text: String) {
        val message = SendMessage()
        message.chatId = chatId.toString()
        message.text = text
        try {
            execute(message)
        } catch (e: Exception) {
            // Log the error but don't stop the process for other chats
            println("Failed to send scheduled message to chat $chatId: ${e.message}")
        }
    }


}
