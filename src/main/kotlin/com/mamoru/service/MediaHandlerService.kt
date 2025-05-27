package com.mamoru.service

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.meta.api.objects.Voice
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Service for handling media in Telegram messages (photos and videos)
 */
@Service
class MediaHandlerService(
    private val geminiAIService: GeminiAIService,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val videoCacheService: VideoCacheService,
    private val tikTokDownloaderService: TikTokDownloaderService,
    private val instagramDownloaderService: InstagramDownloaderService
) {
    private val logger = LoggerFactory.getLogger(MediaHandlerService::class.java)

    /**
     * Handle a photo message and generate a comment using AI
     *
     * @param message The Telegram message containing the photo
     * @param bot The Telegram bot instance
     * @param botToken The Telegram bot token
     * @return The SendMessage object to reply with
     */
    fun handlePhoto(message: Message, bot: TelegramLongPollingBot, botToken: String): SendMessage {
        try {
            // Get the largest photo (best quality)
            val photos = message.photo
            val largestPhoto = photos.maxByOrNull { it.fileSize }

            if (largestPhoto != null) {
                // Generate a comment using Gemini with the actual photo
                val comment = geminiAIService.generatePictureComment(largestPhoto, message.chatId, bot, botToken)

                // Create the comment as a reply to the photo
                val sendMessage = SendMessage()
                sendMessage.setChatId(message.chatId)
                sendMessage.text = comment
                sendMessage.replyToMessageId = message.messageId

                logger.info("Generated picture comment for chat: ${message.chatId}")
                return sendMessage
            } else {
                logger.warn("No photo found in message for chat: ${message.chatId}")
                val errorMessage = SendMessage()
                errorMessage.setChatId(message.chatId)
                errorMessage.text = "Could not process the photo"
                return errorMessage
            }
        } catch (e: Exception) {
            logger.error("Failed to process photo: ${e.message}", e)
            val errorMessage = SendMessage()
            errorMessage.setChatId(message.chatId)
            errorMessage.text = "Failed to process photo: ${e.message}"
            return errorMessage
        }
    }

    /**
     * Handle a TikTok URL by downloading and sending the video
     *
     * @param message The Telegram message containing the TikTok URL
     * @param url The TikTok URL to process
     * @return The SendVideo object or null if processing failed
     */
    fun handleTikTokUrl(message: Message, url: String): SendVideo? {
        try {
            // Check if video is already in cache
            val cachedVideo = videoCacheService.getCachedVideo(url)
            if (cachedVideo != null) {
                logger.info("Using cached TikTok video for URL: $url")
                return createSendVideoObject(message, cachedVideo)
            }

            // Download video if not cached
            logger.info("Downloading TikTok video from URL: $url")
            val downloadedFile = tikTokDownloaderService.downloadVideo(url)
            if (downloadedFile != null) {
                // Cache the video for future use
                videoCacheService.cacheVideo(url, downloadedFile)
                logger.info("Downloaded and cached TikTok video for URL: $url")
                return createSendVideoObject(message, downloadedFile)
            }

            logger.warn("Failed to download TikTok video from URL: $url")
            return null
        } catch (e: Exception) {
            logger.error("Failed to process TikTok video: ${e.message}", e)
            return null
        }
    }

    /**
     * Handle an Instagram URL by downloading and sending the video
     *
     * @param message The Telegram message containing the Instagram URL
     * @param url The Instagram URL to process
     * @return The SendVideo object or null if processing failed
     */
    fun handleInstagramUrl(message: Message, url: String): SendVideo? {
        try {
            // Check if video is already in cache
            val cachedVideo = videoCacheService.getCachedVideo(url)
            if (cachedVideo != null) {
                logger.info("Using cached Instagram video for URL: $url")
                return createSendVideoObject(message, cachedVideo)
            }

            // Download video if not cached
            logger.info("Downloading Instagram video from URL: $url")
            val downloadedFile = instagramDownloaderService.downloadVideo(url)
            if (downloadedFile != null) {
                // Cache the video for future use
                videoCacheService.cacheVideo(url, downloadedFile)
                logger.info("Downloaded and cached Instagram video for URL: $url")
                return createSendVideoObject(message, downloadedFile)
            }

            logger.warn("Failed to download Instagram video from URL: $url")
            return null
        } catch (e: Exception) {
            logger.error("Failed to process Instagram video: ${e.message}", e)
            return null
        }
    }

    /**
     * Create a SendVideo object for a video file
     *
     * @param message The original Telegram message
     * @param videoFile The video file to send
     * @return The SendVideo object
     */
    private fun createSendVideoObject(message: Message, videoFile: File): SendVideo {
        return SendVideo.builder()
            .chatId(message.chatId)
            .video(InputFile(videoFile))
            .caption("${message.from.userName} sent: ${message.text}")
            .build()
    }

    /**
     * Handle an audio message and transcribe it using AI
     *
     * @param message The Telegram message containing the voice
     * @param bot The Telegram bot instance
     * @param botToken The Telegram bot token
     * @return The SendMessage object to reply with
     */
    fun handleAudio(message: Message, bot: TelegramLongPollingBot, botToken: String): SendMessage {
        try {
            // Get the voice object
            val voice = message.voice

            if (voice != null) {
                // Transcribe the audio using Gemini
                val transcription = geminiAIService.transcribeAudioMessage(voice, message.chatId, bot, botToken)

                // Create the transcription as a reply to the audio
                val sendMessage = SendMessage()
                sendMessage.setChatId(message.chatId)
                sendMessage.text = transcription
                sendMessage.replyToMessageId = message.messageId

                logger.info("Transcribed audio message for chat: ${message.chatId}")
                return sendMessage
            } else {
                logger.warn("No voice found in message for chat: ${message.chatId}")
                val errorMessage = SendMessage()
                errorMessage.setChatId(message.chatId)
                errorMessage.text = "Could not process the audio message"
                return errorMessage
            }
        } catch (e: Exception) {
            logger.error("Failed to process audio: ${e.message}", e)
            val errorMessage = SendMessage()
            errorMessage.setChatId(message.chatId)
            errorMessage.text = "Failed to process audio: ${e.message}"
            return errorMessage
        }
    }
}
