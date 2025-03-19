package com.mamoru

import com.mamoru.repository.ChatRepository
import com.mamoru.service.ChatSettingsManagementService
import com.mamoru.service.TikTokDownloaderService
import com.mamoru.service.VideoCacheService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.regex.Matcher
import java.util.regex.Pattern


// Other imports as needed

@Component
class LinkFixerBot(
    @Value("\${telegram.bot.token}") private val botToken: String,
    private val videoCacheService: VideoCacheService,
    private val tikTokDownloaderService: TikTokDownloaderService,
    private val chatRepository: ChatRepository,
    private val chatService: ChatSettingsManagementService
) : TelegramLongPollingBot(botToken) {
    // Existing implementation

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
        if (message.replyToMessage != null && message.from.id == 123616664L) {
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
        }


        sendMessageToChat(123616664L, chatId +": "+ message.from.userName+": "+message.text+": "+message.messageId)

        chatService.addChat(message.chatId);

        chatRepository.addChat(message.chatId)

        val tikTokUrls = findTikTokUrls(text)
        val twitterUrls = findTwitterUrls(text)
        val instagramUrls = findInstagramUrls(text)

        if (twitterUrls.isNotEmpty()) {
            var modifiedText = text

            // Replace all Twitter URLs with fxtwitter.com URLs
            for (url in twitterUrls) {
                val fxTwitterUrl = convertToFxTwitter(url)
                modifiedText = modifiedText.replace(url, fxTwitterUrl)
            }

            // Send the modified message with fxtwitter.com links
            val message = SendMessage()
            message.chatId = chatId
            message.text = "${update.message.from.userName} sent: $modifiedText"
            execute(message)

            // Delete the original message if needed
            // val deleteMessage = DeleteMessage(chatId.toString(), messageId)
            // execute(deleteMessage)
        }

        if (instagramUrls.isNotEmpty()) {
            var modifiedText = text

            // Replace all Instagram URLs with ddinstagram URLs
            for (url in instagramUrls) {
                val ddInstagramUrl = convertToDdInstagram(url)
                modifiedText = modifiedText.replace(url, ddInstagramUrl)
            }

            // Send the modified message with ddinstagram links
            val message = SendMessage()
            message.chatId = chatId
            message.text = "${update.message.from.userName} sent: $modifiedText"
            execute(message)

            // Delete the original message if needed
            // val deleteMessage = DeleteMessage(chatId.toString(), messageId)
            // execute(deleteMessage)
        }


        if (tikTokUrls.isNotEmpty()) {
            for (tikTokUrl in tikTokUrls) {
                try {
                    println("Found TikTok URL: $tikTokUrl")

                    // Check if video is already cached
                    var videoFile = if (videoCacheService.isVideoCached(tikTokUrl)) {
                        videoCacheService.getCachedVideo(tikTokUrl)
                    } else {
                        null
                    }

                    // Download video if not cached
                    if (videoFile == null) {
                        videoFile = tikTokDownloaderService.downloadVideo(tikTokUrl)
                        if (videoFile != null) {
                            videoCacheService.cacheVideo(tikTokUrl, videoFile)
                        }
                    }

                    // Send video if download was successful
                    if (videoFile != null) {
                        val sendVideo = SendVideo.builder()
                            .chatId(chatId)
                            .video(InputFile(videoFile))
                            .caption(update.message.from.userName + " sent:" + update.message.text)
                            .build()

                        execute(sendVideo)
                        println("Sent video to chat: $chatId")
                    } else {
                        println("Failed to download video from: $tikTokUrl")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val FORWARDED_MESSAGE_PATTERN: Pattern = Pattern.compile("^(\\d+): ([^:]+): (.*): (\\d+)$")


    private fun handleReplyToForwardedMessage(replyMessage: Message) {
        val originalForwardedText: String = replyMessage.replyToMessage.getText() ?: ""
        val matcher: Matcher = FORWARDED_MESSAGE_PATTERN.matcher(originalForwardedText)
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


    /**
     * Find TikTok URLs in message text
     */
    private fun findTikTokUrls(text: String): List<String> {
        val tikTokPatterns = listOf(
            "https?://(?:www\\.)?tiktok\\.com/\\@[\\w.-]+/video/[\\d]+[^\\s]*".toRegex(),
            "https?://(?:www\\.)?tiktok\\.com/v/[\\d]+[^\\s]*".toRegex(),
            "https?://(?:www\\.)?vm\\.tiktok\\.com/[\\w]+[^\\s]*".toRegex()
        )

        val matches = mutableListOf<String>()
        for (pattern in tikTokPatterns) {
            pattern.findAll(text).forEach { matches.add(it.value) }
        }

        return matches
    }

    private fun findTwitterUrls(text: String): List<String> {
        val twitterPatterns = listOf(
            // Standard Twitter URLs
            "https?://(?:www\\.)?twitter\\.com/[\\w_]+/status/[\\d]+[^\\s]*".toRegex(),
            // X.com URLs
            "https?://(?:www\\.)?x\\.com/[\\w_]+/status/[\\d]+[^\\s]*".toRegex(),
            // Short t.co URLs
            "https?://t\\.co/[\\w]+[^\\s]*".toRegex()
        )

        val matches = mutableListOf<String>()
        for (pattern in twitterPatterns) {
            pattern.findAll(text).forEach { matches.add(it.value) }
        }

        return matches
    }

    private fun convertToFxTwitter(url: String): String {
        return url.replace("twitter.com", "fxtwitter.com")
            .replace("x.com", "fxtwitter.com")
    }


    /**
     * Find Instagram URLs in message text
     */
    private fun findInstagramUrls(text: String): List<String> {
        val instagramPatterns = listOf(
            // Standard post URLs
            "https?://(?:www\\.)?instagram\\.com/p/[\\w-]+[^\\s]*".toRegex(),
            // Reel URLs
            "https?://(?:www\\.)?instagram\\.com/reel/[\\w-]+[^\\s]*".toRegex(),
            // Stories URLs
            "https?://(?:www\\.)?instagram\\.com/stories/[\\w_.]+/[\\d]+[^\\s]*".toRegex(),
            // Shortened URLs
            "https?://(?:www\\.)?instagr\\.am/[\\w/]+[^\\s]*".toRegex()
        )

        val matches = mutableListOf<String>()
        for (pattern in instagramPatterns) {
            pattern.findAll(text).forEach { matches.add(it.value) }
        }

        return matches
    }

    private fun convertToDdInstagram(url: String): String {
        return url.replace("instagram.com", "ddinstagram.com")
            .replace("instagr.am", "ddinstagram.com")
    }


}
