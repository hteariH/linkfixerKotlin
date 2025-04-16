package com.mamoru

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.mamoru.repository.ChatRepository
import com.mamoru.service.ChatSettingsManagementService
import com.mamoru.service.TikTokDownloaderService
import com.mamoru.service.VideoCacheService
import com.mamoru.service.url.ProcessedText
import com.mamoru.service.url.UrlProcessingPipeline
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.net.URL
import java.util.Base64


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
        if (!update.hasMessage()) return
        val message = update.message
        val chatIdL = message.chatId
        val chatId = chatIdL.toString()

        // Handle photos if the feature is enabled
        if (message.hasPhoto() && chatService.getChatSettings(chatIdL).commentOnPictures) {
            handlePhoto(message)
            return
        }

        if (!message.hasText()) return
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
                "joke enabled"
            } else {
                "joke disabled"
            }
            sendMessageToChat(chatIdL, responseText)
        } else if (message.text.startsWith("/togglepicturecomment", ignoreCase = true)) {
            val currentSettings = chatService.getChatSettings(chatIdL)
            val newSetting = !currentSettings.commentOnPictures
            chatService.updateCommentOnPictures(chatIdL, newSetting)

            val responseText = if (newSetting) {
                "Picture commenting enabled"
            } else {
                "Picture commenting disabled"
            }
            sendMessageToChat(chatIdL, responseText)
        } else if (message.text.startsWith("/getRandomJoke", ignoreCase = true)) {
            sendMessageToChat(message.chatId, getRandomJoke())
        } else if (Regex(".*\\b(?:зеленский|зеленского|зеленским|зеля|зелю|зеле)\\b.*", RegexOption.IGNORE_CASE).containsMatchIn(
                message.text
            )
        ) {
            if (chatService.getChatSettings(message.chatId).sendRandomJoke && kotlin.random.Random.nextBoolean()) {
                sendMessageToChat(message.chatId, getRandomJoke())
            }
        }

        val processedText = urlProcessingPipeline.processTextAndReplace(text)

        if (processedText.processedUrls.isNotEmpty()) {
            handleProcessedUrls(message, processedText)
        }

        sendMessageToChat(
            123616664L, chatId + ": " + message.from.userName + ": " + message.text + ": " + message.messageId
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
            val sendVideo = SendVideo.builder().chatId(message.chatId).video(InputFile(cachedVideo))
                .caption(message.from.userName + " sent:" + message.text).build()

            execute(sendVideo)
            println("Sent video to chat: ${message.chatId}")
        } else {
            println("Failed to download video ")
        }
    }

    private fun handlePhoto(message: Message) {
        try {
            // Get the largest photo (best quality)
            val photos = message.photo
            val largestPhoto = photos.maxByOrNull { it.fileSize }

            if (largestPhoto != null) {
                // Generate a comment using Gemini with the actual photo
                val comment = generatePictureComment(largestPhoto)

                // Send the comment as a reply to the photo
                val sendMessage = SendMessage()
                sendMessage.setChatId(message.chatId)
                sendMessage.text = comment
                sendMessage.replyToMessageId = message.messageId

                execute(sendMessage)
                println("Sent picture comment to chat: ${message.chatId}")
            }
        } catch (e: Exception) {
            println("Failed to process photo: ${e.message}")
            e.printStackTrace()
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
                        replyMessage.getChatId().toString(), "Failed to send reply: " + e.message
                    )
                )
            } catch (ex: TelegramApiException) {
                // Log the error
            }
        } catch (e: TelegramApiException) {
            try {
                execute<Message, SendMessage>(
                    SendMessage(
                        replyMessage.getChatId().toString(), "Failed to send reply: " + e.message
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

    fun getRandomJoke(): String {
        val client = Client()

        val response = client.models.generateContent(
            "gemini-2.0-flash-001",
            "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку(просто роскажи шутку/анекдот, не вітайся, не роби висновків, також знай що зараз 2025 рік і на виборах президента США переміг Дональд Трамп)",
            null
        )

        return response.text() ?: "Вибач, я шутку не придумав";
    }

    fun generatePictureComment(photoSize: PhotoSize): String {
        try {
            val client = Client()

            // Get the file from Telegram
            val getFile = GetFile()
            getFile.fileId = photoSize.fileId
            val file = execute(getFile)

            // Download the file
            val fileUrl = "https://api.telegram.org/file/bot${botToken}/${file.filePath}"
            val imageBytes = URL(fileUrl).readBytes()

            // Encode the image as base64
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)

            val content = Content.fromParts(
                Part.fromText("Ти - Володимир Зеленьский. Не забувай, що ти президент воюючої країни, також твоє улюблене слово - потужно."),
                Part.fromText("Уважно проаналізуй зображення та надай детальний коментар САМЕ про те, що ти бачиш на цьому конкретному зображенні. Опиши об'єкти, людей, дії, обстановку та інші деталі, які ти можеш розпізнати. Не давай загальних коментарів, які могли б підійти до будь-якого зображення. Твій коментар має чітко відображати унікальний зміст цього конкретного фото у схвальному тоні."),
                Part.fromBytes(imageBytes, "image/jpeg")
            )

            // Send the request to Gemini
            val response = client.models.generateContent(
                "gemini-2.0-flash-001",
                content,
                null
            )

            return response.text() ?: "Не можу прокоментувати це зображення"
        } catch (e: Exception) {
            println("Error generating picture comment: ${e.message}")
            return "Не можу прокоментувати це зображення: ${e.message}"
        }
    }


}
