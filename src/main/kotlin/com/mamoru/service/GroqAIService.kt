package com.mamoru.service

import com.mamoru.service.MessageCacheService.CachedMessage
import com.mamoru.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.util.MimeTypeUtils
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.net.URL

@Service
@ConditionalOnProperty(name = ["ai.provider"], havingValue = "groq")
class GroqAIService(
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val botRegistryService: BotRegistryService,
    @Value("\${groq.api-key}") private val apiKey: String,
    @Value("\${groq.base-url:https://api.groq.com/openai}") private val baseUrl: String
) : AIService {

    @Autowired
    private lateinit var messageAnalyzerService: MessageAnalyzerService

    private val logger = LoggerFactory.getLogger(GroqAIService::class.java)

    private val chatModel: OpenAiChatModel by lazy {
        val api = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .build()
        OpenAiChatModel.builder()
            .openAiApi(api)
            .build()
    }

    private fun generateWithModels(
        messages: List<org.springframework.ai.chat.messages.Message>,
        failureMessage: String,
        modelCandidates: List<String> = Constants.AI.GROQ_MODEL_CANDIDATES
    ): String {
        logger.info("Generating content with Groq models: $modelCandidates")
        var lastError: Exception? = null
        for (model in modelCandidates) {
            logger.info("Generating content with Groq model $model")
            try {
                val options = OpenAiChatOptions.builder().model(model).build()
                val response = chatModel.call(Prompt(messages, options))
                val text = response.result?.output?.text
                if (!text.isNullOrBlank()) return text
            } catch (e: Exception) {
                lastError = e
                logger.warn("Groq model $model failed: ${e.message}")
            }
        }
        if (lastError != null) logger.error("All Groq models failed", lastError)
        return failureMessage
    }

    /**
     * Tries vision models when images are present. Falls back to text-only models
     * (without images) if all vision models fail.
     */
    private fun generateWithOptionalImages(
        systemText: String?,
        userText: String,
        imageBytesList: List<ByteArray>,
        failureMessage: String
    ): String {
        val systemMessages = if (systemText != null) listOf(SystemMessage(systemText)) else emptyList()

        if (imageBytesList.isNotEmpty()) {
            try {
                val mediaList = imageBytesList.map { bytes ->
                    Media(MimeTypeUtils.IMAGE_JPEG, ByteArrayResource(bytes))
                }
                val userMsg = UserMessage.builder()
                    .text(userText)
                    .media(mediaList)
                    .build()
                val result = generateWithModels(
                    systemMessages + userMsg,
                    "",
                    Constants.AI.GROQ_VISION_MODEL_CANDIDATES
                )
                if (result.isNotBlank()) return result
                logger.warn("All Groq vision models failed, retrying text-only")
            } catch (e: Exception) {
                logger.warn("Failed to build vision request: ${e.message}")
            }
        }

        return generateWithModels(systemMessages + UserMessage(userText), failureMessage)
    }

    override fun getRandomJoke(chatId: Long?): String {
        val prompt = if (chatId != null) {
            chatSettingsManagementService.getChatSettings(chatId).jokePrompt
        } else {
            Constants.AI.DEFAULT_JOKE_PROMPT
        }
        return generateWithModels(listOf(UserMessage(prompt)), Constants.AI.DEFAULT_JOKE_FAILURE_MESSAGE)
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

        val userParts = mutableListOf<String>()
        if (replyText != null && from != null) {
            val cleanReply = replyText.replace("@$botUsername", "", ignoreCase = true)
            if (from.endsWith(botUsername.lowercase(), true) || from.equals("Зеленський", true)) {
                userParts.add("This is the message I'm replying to: $cleanReply, message is sent by you")
            } else {
                userParts.add("This is the message I'm replying to: $cleanReply, message is sent by: $from")
            }
        }
        userParts.add("Respond to this message: ${messageText.replace("@$botUsername", "", ignoreCase = true)}")

        val imageBytesList = mutableListOf<ByteArray>()
        if (replyPhoto != null && telegramClient != null) {
            val imageBytes = downloadImage(telegramClient, replyPhoto.fileId)
            if (imageBytes != null) {
                imageBytesList.add(imageBytes)
                userParts.add(Constants.AI.PICTURE_ANALYSIS_INSTRUCTION)
            } else {
                userParts.add("Note: The message I'm replying to contained a photo, but I couldn't download it.")
            }
        } else if (replyPhoto != null) {
            userParts.add("Note: The message I'm replying to contained a photo, but I can't see it right now.")
        }

        return generateWithOptionalImages(
            systemText = picturePrompt,
            userText = userParts.joinToString("\n"),
            imageBytesList = imageBytesList,
            failureMessage = Constants.AI.DEFAULT_PICTURE_FAILURE_MESSAGE
        )
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
            val rawSavedMessages = messageAnalyzerService.readSavedMessages(userid)
            if (rawSavedMessages.isNullOrEmpty()) {
                logger.warn("No saved messages found for impersonation")
                return ImpersonationResponse("I don't have enough data to impersonate this person.")
            }
            // Keep only the most recent portion to stay within Groq's token limits
            val savedMessages = if (rawSavedMessages.length > Constants.AI.GROQ_MAX_SAVED_MESSAGES_CHARS) {
                logger.info("[{}] Truncating saved messages from {} to {} chars for userId={}",
                    botUsername, rawSavedMessages.length, Constants.AI.GROQ_MAX_SAVED_MESSAGES_CHARS, userid)
                rawSavedMessages.takeLast(Constants.AI.GROQ_MAX_SAVED_MESSAGES_CHARS)
            } else rawSavedMessages

            val hasRecentMessages = recentMessages.isNotEmpty()

            fun buildContext(includeRecentMessages: Boolean, includeContextHint: Boolean): Pair<String, List<ByteArray>> {
                val userParts = mutableListOf<String>()
                val imageBytesList = mutableListOf<ByteArray>()

                if (includeRecentMessages) {
                    val cappedRecent = recentMessages.takeLast(Constants.AI.GROQ_MAX_RECENT_MESSAGES)
                    userParts.add("Here is the recent chat history for context (oldest first):")
                    for (msg in cappedRecent) {
                        val msgText = msg.text?.replace("@$botUsername", "", ignoreCase = true)?.trim() ?: "(no text)"
                        userParts.add("[${msg.displayName()}]: $msgText")
                        if (msg.photoFileId != null && telegramClient != null) {
                            val imageBytes = downloadImage(telegramClient, msg.photoFileId)
                            if (imageBytes != null) imageBytesList.add(imageBytes)
                        }
                    }
                }

                if (replyChain.isNotEmpty()) {
                    val cappedChain = replyChain.takeLast(Constants.AI.GROQ_MAX_REPLY_CHAIN_MESSAGES)
                    userParts.add("Here is the conversation thread leading up to this message (oldest first):")
                    for (msg in cappedChain) {
                        val msgText = msg.text?.replace("@$botUsername", "", ignoreCase = true)?.trim() ?: "(no text)"
                        userParts.add("[${msg.displayName()}]: $msgText")
                        if (msg.photoFileId != null && telegramClient != null) {
                            val imageBytes = downloadImage(telegramClient, msg.photoFileId)
                            if (imageBytes != null) imageBytesList.add(imageBytes)
                            else userParts.add("[image — could not be loaded]")
                        }
                    }
                }

                if (replyText != null && from != null) {
                    userParts.add(
                        "This is the message being directly replied to: ${
                            replyText.replace("@$botUsername", "", ignoreCase = true)
                        }, sent by: $from"
                    )
                }

                userParts.add(
                    "Respond to this message as the person I'm impersonating: ${
                        messageText.replace("@$botUsername", "", ignoreCase = true)
                    }"
                )

                if (replyPhoto != null && telegramClient != null) {
                    val imageBytes = downloadImage(telegramClient, replyPhoto.fileId)
                    if (imageBytes != null) {
                        userParts.add("There is an image in the message I'm replying to. Consider it in your response if relevant.")
                        imageBytesList.add(imageBytes)
                    } else {
                        userParts.add("Note: The message I'm replying to contained a photo, but I couldn't download it.")
                    }
                }

                val contextHint = if (includeContextHint)
                    "\n\nIMPORTANT: If you feel you need recent general chat history to understand the conversational context before responding, reply with exactly '[NEED_CONTEXT]' and nothing else. Otherwise, respond normally as the impersonated person."
                else ""

                val fullPrompt = buildString {
                    appendLine("""
                        You are now impersonating a person whose messages are provided below.
                        Your task is to respond to the given message in the same style, tone, and personality as the person you're impersonating.
                        Use the message history to understand their communication style, vocabulary, topics of interest, and personality traits.

                        If the person you are replying to is another bot or AI, be concise and avoid getting stuck in a loop.

                        Here is the message history of the person you're impersonating:

                        $savedMessages

                        Based on this history, respond to the following message as if you were this person.$contextHint
                    """.trimIndent())
                    appendLine("---")
                    appendLine(userParts.joinToString("\n"))
                }

                return Pair(fullPrompt, imageBytesList)
            }

            // First pass: no recent messages; hint model it can request them if needed
            val (firstPrompt, firstImages) = buildContext(includeRecentMessages = false, includeContextHint = hasRecentMessages)
            val firstResponse = generateWithOptionalImages(
                systemText = null,
                userText = firstPrompt,
                imageBytesList = firstImages,
                failureMessage = "I couldn't generate a response at this time."
            )

            if (firstResponse.trim() == "[NEED_CONTEXT]" && hasRecentMessages) {
                logger.info("[{}] Model requested recent context for userId={}, retrying with {} messages",
                    botUsername, userid, recentMessages.size)
                val (secondPrompt, secondImages) = buildContext(includeRecentMessages = true, includeContextHint = false)
                val finalResponse = generateWithOptionalImages(
                    systemText = null,
                    userText = secondPrompt,
                    imageBytesList = secondImages,
                    failureMessage = "I couldn't generate a response at this time."
                )
                return ImpersonationResponse(finalResponse, userid)
            }

            return ImpersonationResponse(firstResponse, userid)
        } catch (e: Exception) {
            logger.error("Error generating impersonation response: ${e.message}", e)
            return ImpersonationResponse("I couldn't generate a response at this time.")
        }
    }

    override fun streamMentionResponse(
        messageText: String,
        chatId: Long,
        replyText: String?,
        from: String?,
        replyPhoto: PhotoSize?,
        telegramClient: TelegramClient?,
        botUsername: String
    ): Sequence<String> = emptySequence()

    override fun streamImpersonationResponse(
        messageText: String,
        replyText: String?,
        from: String?,
        replyPhoto: PhotoSize?,
        telegramClient: TelegramClient?,
        botUsername: String,
        userid: Long,
        replyChain: List<CachedMessage>,
        recentMessages: List<CachedMessage>
    ): Sequence<String> = emptySequence()
}
