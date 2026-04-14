package com.mamoru.service

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.mamoru.service.MessageCacheService.CachedMessage
import com.mamoru.util.Constants
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.generics.TelegramClient
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize

@Service
@ConditionalOnProperty(name = ["ai.provider"], havingValue = "gemini", matchIfMissing = true)
class GeminiAIService(
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val botRegistryService: BotRegistryService
) : AIService {
    @Autowired
    private lateinit var messageAnalyzerService: MessageAnalyzerService
    private val logger = LoggerFactory.getLogger(GeminiAIService::class.java)
    private val client = Client()

    private fun generateWithModels(content: Content, failureMessage: String): String {
        var lastError: Exception? = null
        for (model in Constants.AI.MODEL_CANDIDATES) {
            logger.info("Generating content with model $model")
            try {
                val response = client.models.generateContent(model, content, null)
                val text = response.text()
                if (!text.isNullOrBlank()) return text
            } catch (e: Exception) {
                lastError = e
                logger.warn("Model $model failed to generate content: ${e.message}")
            }
        }
        if (lastError != null) logger.error("All models failed to generate content", lastError)
        return failureMessage
    }

    private fun generateWithModels(prompt: String, failureMessage: String): String {
        var lastError: Exception? = null
        for (model in Constants.AI.MODEL_CANDIDATES) {
            try {
                val response = client.models.generateContent(model, prompt, null)
                val text = response.text()
                if (!text.isNullOrBlank()) return text
            } catch (e: Exception) {
                lastError = e
                logger.warn("Model $model failed to generate content: ${e.message}")
            }
        }
        if (lastError != null) logger.error("All models failed to generate content", lastError)
        return failureMessage
    }

    override fun getRandomJoke(chatId: Long?): String {
        val prompt = if (chatId != null) {
            chatSettingsManagementService.getChatSettings(chatId).jokePrompt
        } else {
            Constants.AI.DEFAULT_JOKE_PROMPT
        }
        return generateWithModels(prompt, Constants.AI.DEFAULT_JOKE_FAILURE_MESSAGE)
    }

    private fun downloadImage(telegramClient: TelegramClient, fileId: String): ByteArray? = try {
        val tgFile = telegramClient.execute(GetFile.builder().fileId(fileId).build())
        telegramClient.downloadFile(tgFile).readBytes()
    } catch (e: Exception) {
        logger.warn("Could not download image $fileId: ${e.message}")
        null
    }

    override fun generateMentionResponse(
        messageText: String,
        chatId: Long,
        replyText: String?,
        from: String?,
        replyPhoto: PhotoSize?,
        telegramClient: TelegramClient?,
        botUsername: String
    ): String {
        val picturePrompt = chatSettingsManagementService.getChatSettings(chatId).picturePrompt
        val contentParts = mutableListOf<Part>()
        contentParts.add(Part.fromText(picturePrompt))

        if (replyText != null && from != null) {
            if (from.endsWith(botUsername.lowercase(), true) || from.equals("Зеленський", true)) {
                contentParts.add(
                    Part.fromText("This is the message I'm replying to: ${replyText.replace("@$botUsername", "", ignoreCase = true)}, message is sent by you")
                )
            } else {
                contentParts.add(
                    Part.fromText("This is the message I'm replying to: ${replyText.replace("@$botUsername", "", ignoreCase = true)}, message is sent by: $from")
                )
            }
        }

        contentParts.add(
            Part.fromText("Respond to this message: ${messageText.replace("@$botUsername", "", ignoreCase = true)}")
        )

        if (replyPhoto != null && telegramClient != null) {
            val imageBytes = downloadImage(telegramClient, replyPhoto.fileId)
            if (imageBytes != null) {
                contentParts.add(Part.fromText(Constants.AI.PICTURE_ANALYSIS_INSTRUCTION))
                contentParts.add(Part.fromBytes(imageBytes, "image/jpeg"))
            } else {
                contentParts.add(Part.fromText("Note: The message I'm replying to contained a photo, but I couldn't download it. Please acknowledge this in your response."))
            }
        } else if (replyPhoto != null) {
            contentParts.add(Part.fromText("Note: The message I'm replying to contained a photo, but I can't see it right now. Please acknowledge this in your response."))
        }

        val content = Content.fromParts(*contentParts.toTypedArray())
        return generateWithModels(content, Constants.AI.DEFAULT_PICTURE_FAILURE_MESSAGE)
    }

    override fun generateImpersonationResponse(
        messageText: String,
        replyText: String?,
        from: String?,
        replyPhoto: PhotoSize?,
        telegramClient: TelegramClient?,
        botUsername: String,
        userid: Long,
        replyChain: List<CachedMessage>,
        recentMessages: List<CachedMessage>
    ): ImpersonationResponse {
        try {
            val savedMessages = messageAnalyzerService.readSavedMessages(userid)
            if (savedMessages.isNullOrEmpty()) {
                logger.warn("No saved messages found for impersonation")
                return ImpersonationResponse("I don't have enough data to impersonate this person.")
            }

            val hasRecentMessages = recentMessages.isNotEmpty()

            fun buildParts(includeRecentMessages: Boolean, includeContextHint: Boolean): List<Part> {
                val parts = mutableListOf<Part>()

                val systemPrompt = buildString {
                    append("""
                        You are now impersonating a person whose messages are provided below.
                        Your task is to respond to the given message in the same style, tone, and personality as the person you're impersonating.
                        Use the message history to understand their communication style, vocabulary, topics of interest, and personality traits.
                        
                        If the person you are replying to is another bot or AI, be concise and avoid getting stuck in a loop.

                        Here is the message history of the person you're impersonating:

                        $savedMessages

                        Based on this history, respond to the following message as if you were this person.
                    """.trimIndent())
                    if (includeContextHint) {
                        append("\n\nIMPORTANT: If you feel you need recent general chat history to understand the conversational context before responding, reply with exactly '[NEED_CONTEXT]' and nothing else. Otherwise, respond normally as the impersonated person.")
                    }
                }
                parts.add(Part.fromText(systemPrompt))

                if (includeRecentMessages) {
                    parts.add(Part.fromText("Here is the recent chat history for context (oldest first):"))
                    for (msg in recentMessages) {
                        val msgText = msg.text?.replace("@$botUsername", "", ignoreCase = true)?.trim() ?: "(no text)"
                        parts.add(Part.fromText("[${msg.displayName()}]: $msgText"))
                        if (msg.photoFileId != null && telegramClient != null) {
                            val imageBytes = downloadImage(telegramClient, msg.photoFileId)
                            if (imageBytes != null) parts.add(Part.fromBytes(imageBytes, "image/jpeg"))
                        }
                    }
                }

                if (replyChain.isNotEmpty()) {
                    parts.add(Part.fromText("Here is the conversation thread leading up to this message (oldest first):"))
                    for (msg in replyChain) {
                        val msgText = msg.text?.replace("@$botUsername", "", ignoreCase = true)?.trim() ?: "(no text)"
                        parts.add(Part.fromText("[${msg.displayName()}]: $msgText"))
                        if (msg.photoFileId != null && telegramClient != null) {
                            val imageBytes = downloadImage(telegramClient, msg.photoFileId)
                            if (imageBytes != null) parts.add(Part.fromBytes(imageBytes, "image/jpeg"))
                            else parts.add(Part.fromText("[image — could not be loaded]"))
                        }
                    }
                }

                if (replyText != null && from != null) {
//                    val fromPrefix = if (botRegistryService.isBot(from)) "[BOT] " else ""
                    parts.add(
                        Part.fromText(
                            "This is the message being directly replied to: ${
                                replyText.replace("@$botUsername", "", ignoreCase = true)
                            }, sent by: " +
//                                    "$fromPrefix" +
                                    from
                        )
                    )
                }

                parts.add(
                    Part.fromText(
                        "Respond to this message as the person I'm impersonating: ${
                            messageText.replace("@$botUsername", "", ignoreCase = true)
                        }"
                    )
                )

                if (replyPhoto != null && telegramClient != null) {
                    val imageBytes = downloadImage(telegramClient, replyPhoto.fileId)
                    if (imageBytes != null) {
                        parts.add(Part.fromText("There is an image in the message I'm replying to. Consider it in your response if relevant."))
                        parts.add(Part.fromBytes(imageBytes, "image/jpeg"))
                    } else {
                        parts.add(Part.fromText("Note: The message I'm replying to contained a photo, but I couldn't download it."))
                    }
                }

                return parts
            }

            // First pass: no recent messages; hint model it can request them if needed
            val firstPassParts = buildParts(includeRecentMessages = false, includeContextHint = hasRecentMessages)
            val firstResponse = generateWithModels(
                Content.fromParts(*firstPassParts.toTypedArray()),
                "I couldn't generate a response at this time."
            )

            if (firstResponse.trim() == "[NEED_CONTEXT]" && hasRecentMessages) {
                logger.info("[{}] Model requested recent context for userId={}, retrying with {} messages",
                    botUsername, userid, recentMessages.size)
                val secondPassParts = buildParts(includeRecentMessages = true, includeContextHint = false)
                val finalResponse = generateWithModels(
                    Content.fromParts(*secondPassParts.toTypedArray()),
                    "I couldn't generate a response at this time."
                )
                return ImpersonationResponse(finalResponse, userid)
            }

            return ImpersonationResponse(firstResponse, userid)
        } catch (e: Exception) {
            logger.error("Error generating impersonation response: ${e.message}", e)
            return ImpersonationResponse("I couldn't generate a response at this time.")
        }
    }
}
