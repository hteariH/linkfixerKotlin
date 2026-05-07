package com.mamoru

import com.mamoru.service.*
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessageDraft
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

open class HydraManagerBot(
    private val botToken: String,
    open val botName: String,
    private val commandHandlerService: CommandHandlerService,
    private val messageProcessorService: MessageProcessorService,
    private val chatSettingsManagementService: ChatSettingsManagementService,
    private val messageAnalyzerService: MessageAnalyzerService,
    private val messageCacheService: MessageCacheService,
    private val starBalanceService: StarBalanceService,
    // Non-null for managed bots: always impersonates this user when mentioned
    private val targetUserId: Long? = null
) : LongPollingSingleThreadUpdateConsumer {

    private val logger = LoggerFactory.getLogger(HydraManagerBot::class.java)
    open val telegramClient = OkHttpTelegramClient(botToken)

    override fun consume(update: Update) {
        logger.debug("Received update: {}", update)

        // Handle top-up button presses
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update)
            return
        }

        // Handle payment pre-checkout: must be approved before message arrives
        if (update.hasPreCheckoutQuery()) {
            handlePreCheckoutQuery(update)
            return
        }

        if (!update.hasMessage()) return
        val message = update.message
        val chatId = message.chatId

        try {
            // Handle successful Stars payment — credit balance
            if (message.successfulPayment != null) {
                handleSuccessfulPayment(message)
                return
            }

            messageCacheService.cache(message)
            if (targetUserId == null) {
                messageAnalyzerService.analyzeMessageIfFromTargetUser(message)
            }
            val hasContent = message.hasText() || (targetUserId != null && message.caption != null)
            if (targetUserId != null) {
                logger.debug("[{}] Update received: chat={} msgId={} from=@{} hasText={} hasCaption={} replyToId={}",
                    botName, chatId, message.messageId,
                    message.from?.userName,
                    message.hasText(), message.caption != null,
                    message.replyToMessage?.messageId)
            }
            if (!hasContent) {
                if (targetUserId != null) logger.debug("[{}] Skipping — no text/caption", botName)
                return
            }

            if (targetUserId == null) {
                val commandResult = commandHandlerService.handleCommand(message)
                if (commandResult.isCommand) {
                    commandResult.responseText?.let { sendMessageToChat(chatId, it) }
                    return
                }
            }

            processTextMessage(message)

        } catch (e: Exception) {
            logger.error("Error processing update: ${e.message}", e)
        }
    }

    private fun handleCallbackQuery(update: Update) {
        val query = update.callbackQuery
        val data = query.data ?: return
        if (!StarBalanceService.TOP_UP_OPTIONS.containsKey(data)) return

        val amount = StarBalanceService.TOP_UP_OPTIONS[data] ?: return
        val payload = StarBalanceService.payloadFor(data) ?: return
        val chatId = query.message?.chatId?.toString() ?: return

        try {
            telegramClient.execute(
                AnswerCallbackQuery.builder().callbackQueryId(query.id).build()
            )
        } catch (e: TelegramApiException) {
            logger.warn("Failed to answer callback query: ${e.message}")
        }

        try {
            val invoice = SendInvoice.builder()
                .chatId(chatId)
                .title("Пополнить на $amount ⭐")
                .description("$amount звёзд • ${StarBalanceService.COST_PER_MESSAGE} ⭐ за сообщение")
                .payload(payload)
                .currency("XTR")
                .prices(listOf(LabeledPrice("$amount звёзд", amount)))
                .build()
            telegramClient.execute(invoice)
            logger.info("Sent $amount ⭐ invoice to chatId=$chatId via callback")
        } catch (e: TelegramApiException) {
            logger.error("Failed to send invoice for callback data=$data: ${e.message}", e)
        }
    }

    private fun handlePreCheckoutQuery(update: Update) {
        val query = update.preCheckoutQuery
        try {
            val answer = AnswerPreCheckoutQuery(query.id, true)
            telegramClient.execute(answer)
            logger.info("Approved pre-checkout query id=${query.id} from userId=${query.from.id}")
        } catch (e: TelegramApiException) {
            logger.error("Failed to answer pre-checkout query: ${e.message}", e)
        }
    }

    private fun handleSuccessfulPayment(message: Message) {
        val payment = message.successfulPayment
        val userId = message.from?.id ?: return
        if (payment.invoicePayload in StarBalanceService.INVOICE_PAYLOADS && payment.currency == "XTR") {
            val amount = payment.totalAmount
            starBalanceService.addStars(userId, amount)
            val newBalance = starBalanceService.getBalance(userId)
            sendMessageToChat(message.chatId,
                "Баланс пополнен! Начислено $amount ⭐. Текущий баланс: $newBalance ⭐"
            )
            logger.info("Stars payment: userId=$userId credited $amount ⭐, balance=$newBalance")
        }
    }

    /**
     * Quick check whether this bot would be triggered by the message,
     * used to gate the balance check before running AI generation.
     */
    private fun wouldBotTrigger(message: Message): Boolean {
        val text = message.text ?: message.caption ?: ""
        val isPrivateChat = message.chat.isUserChat
        val byMention = text.contains("@$botName", ignoreCase = true)
        val byReply = message.replyToMessage?.from?.userName
            ?.equals(botName, ignoreCase = true) == true
        val byPrivate = targetUserId != null && isPrivateChat
        val byOwnMsg = targetUserId != null && !isPrivateChat &&
            message.replyToMessage?.messageId?.let {
                messageCacheService.isOwnMessage(botName, message.chatId, it)
            } == true
        return byMention || byReply || byPrivate || byOwnMsg
    }

    private fun processTextMessage(message: Message) {
        val userId = message.from?.id

        // Balance gate: check before running expensive AI generation
        if (userId != null && wouldBotTrigger(message)) {
            if (!starBalanceService.hasEnoughBalance(userId)) {
                val balance = starBalanceService.getBalance(userId)
                sendMessageToChat(message.chatId,
                    StarBalanceService.promptTopUp(StarBalanceService.COST_PER_MESSAGE, botName)
                )
                starBalanceService.sendStarInvoice(telegramClient, message.chatId, userId, message.messageId)
                return
            }
        }

        val replyChain = if (targetUserId != null)
            messageCacheService.getReplyChain(message.chatId, message.messageId)
        else emptyList()

        val recentMessages = if (targetUserId != null) {
            val excludeIds = (replyChain.map { it.messageId } + message.messageId).toSet()
            messageCacheService.getRecentMessages(message.chatId, limit = 30, excludeIds = excludeIds)
        } else emptyList()

        if (targetUserId != null) {
            logger.debug("[{}] Processing message, replyChain={} msgs, recentContext={} msgs",
                botName, replyChain.size, recentMessages.size)
        }

        val result = messageProcessorService.processTextMessage(
            message, botName, targetUserId, replyChain, recentMessages,
            telegramClient
        )

        val settings = chatSettingsManagementService.getChatSettings(message.chatId)
        val isManaged = targetUserId != null

        if (isManaged || settings.commentOnPictures) {
            result.mentionResponseStream?.let { stream ->
                handleStreamResponse(message, stream, isManaged)
                
                // Deduct stars only when a response was actually sent
                if (userId != null) {
                    starBalanceService.deductStars(userId, StarBalanceService.COST_PER_MESSAGE)
                    logger.info("[{}] Deducted ${StarBalanceService.COST_PER_MESSAGE} ⭐ from userId=$userId", botName)
                }
            }
        }

        if (settings.commentOnPictures) {
            result.jokeResponse?.let { jokeText ->
                sendMessageToChat(message.chatId, jokeText)
            }
        }
    }

    private fun handleStreamResponse(originalMessage: Message, stream: Sequence<String>, isManaged: Boolean) {
        var currentText = ""
        var lastUpdateText = ""
        var lastUpdateTime = 0L
        val updateIntervalMs = 500L // Faster updates with sendMessageDraft
        val draftId = originalMessage.messageId.toLong()

        try {
            for (chunk in stream) {
                currentText += chunk
                val now = System.currentTimeMillis()

                if (now - lastUpdateTime > updateIntervalMs && currentText != lastUpdateText && currentText.isNotBlank()) {
                    val textToSet = if (currentText.length > 4096) currentText.substring(0, 4096) else currentText
                    
                    try {
                        telegramClient.execute(
                            SendMessageDraft.builder()
                                .chatId(originalMessage.chatId)
                                .text(textToSet)
                                .draftId(draftId.toInt())
                                .build()
                        )
                        lastUpdateText = currentText
                        lastUpdateTime = now
                    } catch (e: TelegramApiException) {
                        logger.warn("Failed to send message draft: ${e.message}")
                    }
                }
            }

            // Final update - send real message(s)
            if (currentText.isNotBlank()) {
                val finalParts = splitAndTruncate(currentText)
                
                finalParts.forEachIndexed { index, part ->
                    val sent = telegramClient.execute(
                        SendMessage.builder()
                            .chatId(originalMessage.chatId.toString())
                            .replyToMessageId(if (index == 0) originalMessage.messageId else null)
                            .text(part)
                            .build()
                    )
                    
                    if (isManaged && index == 0) {
                        messageCacheService.cacheSentMessage(
                            chatId = originalMessage.chatId,
                            messageId = sent.messageId,
                            text = part,
                            botUsername = botName,
                            replyToMessageId = originalMessage.messageId
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error during stream processing: ${e.message}", e)
        }
    }

    private fun splitAndTruncate(text: String): List<String> {
        val separator = "----------------------------------------"
        val telegramMaxLen = 4096
        return text
            .split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part -> if (part.length > telegramMaxLen) part.substring(0, telegramMaxLen) else part }
    }

    open fun sendMessageToChat(chatId: Long, text: String) {
        val parts = splitAndTruncate(text)
        if (parts.isEmpty()) return
        for (part in parts) {
            val message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(part)
                .build()
            try {
                val sent = telegramClient.execute(message)
                logger.info("Sent message ${sent.messageId} to chat $chatId")
            } catch (e: Exception) {
                logger.error("Failed to send message to chat $chatId: ${e.message}", e)
            }
        }
    }
}
