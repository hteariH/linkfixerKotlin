package com.mamoru.service

import com.mamoru.entity.UserBalance
import com.mamoru.repository.UserBalanceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient

@Service
class StarBalanceService(
    private val userBalanceRepository: UserBalanceRepository,
    private val primaryBotHolder: PrimaryBotHolder
) {
    private val logger = LoggerFactory.getLogger(StarBalanceService::class.java)

    companion object {
        const val COST_PER_MESSAGE = 7
        const val TOP_UP_AMOUNT = 100
        const val INVOICE_PAYLOAD = "stars_topup_100"
    }

    fun getBalance(userId: Long): Int =
        userBalanceRepository.findById(userId).map { it.starBalance }.orElse(COST_PER_MESSAGE)

    fun hasEnoughBalance(userId: Long): Boolean =
        getBalance(userId) >= COST_PER_MESSAGE

    fun deductStars(userId: Long, amount: Int = COST_PER_MESSAGE) {
        val current = userBalanceRepository.findById(userId).orElse(UserBalance(userId, COST_PER_MESSAGE))
        val newBalance = maxOf(0, current.starBalance - amount)
        userBalanceRepository.save(current.copy(starBalance = newBalance))
        logger.info("Deducted $amount ⭐ from userId=$userId, balance: ${current.starBalance} → $newBalance")
    }

    fun addStars(userId: Long, amount: Int) {
        val current = userBalanceRepository.findById(userId).orElse(UserBalance(userId, COST_PER_MESSAGE))
        val newBalance = current.starBalance + amount
        userBalanceRepository.save(current.copy(starBalance = newBalance))
        logger.info("Added $amount ⭐ to userId=$userId, balance: ${current.starBalance} → $newBalance")
    }

    /**
     * Sends a Stars invoice via the primary bot (HydraManagerBot).
     * Strategy:
     *  1. Try sending to current chat via primary bot's client (if primary bot is in the chat).
     *  2. Try sending to user's private chat via primary bot's client (if user has started it).
     *  3. Fall back: send a text message via the caller's client with a mention of the primary bot.
     *
     * [callerClient] is the TelegramClient of the bot that triggered the balance check.
     * [chatId] is the chat where the interaction happened.
     * [userId] is the user who triggered the bot.
     * [replyToMessageId] is the message to reply to in the current chat.
     */
    fun sendStarInvoice(
        callerClient: TelegramClient,
        chatId: Long,
        userId: Long,
        replyToMessageId: Int
    ) {
        val primaryClient = primaryBotHolder.client
        val primaryBotName = primaryBotHolder.botName

        if (primaryClient == null) {
            logger.warn("Primary bot client not set in PrimaryBotHolder, cannot send invoice")
            return
        }

        // 1. Try to send invoice to the current chat via the primary bot
        if (sendInvoice(primaryClient, chatId.toString(), replyToMessageId)) return

        // 2. Try to send invoice to the user's private chat via the primary bot
        if (sendInvoice(primaryClient, userId.toString(), null)) return

        // 3. Fallback: instruct user to top up via the primary bot
        try {
            val msg = SendMessage.builder()
                .chatId(chatId.toString())
                .replyToMessageId(replyToMessageId)
                .text("У тебя недостаточно звёзд ⭐. " +
                    "Пополни баланс через @$primaryBotName (${COST_PER_MESSAGE} ⭐ за сообщение).")
                .build()
            callerClient.execute(msg)
        } catch (e: TelegramApiException) {
            logger.error("Failed to send fallback message to chatId=$chatId: ${e.message}", e)
        }
    }

    private fun sendInvoice(client: TelegramClient, chatId: String, replyToMessageId: Int?): Boolean {
        return try {
            val builder = SendInvoice.builder()
                .chatId(chatId)
                .title("Пополнение баланса ⭐")
                .description("100 звёзд для общения с ботом (${COST_PER_MESSAGE} ⭐ за сообщение)")
                .payload(INVOICE_PAYLOAD)
                .currency("XTR")
                .prices(listOf(LabeledPrice("100 звёзд", TOP_UP_AMOUNT)))
            if (replyToMessageId != null) builder.replyToMessageId(replyToMessageId)
            client.execute(builder.build())
            logger.info("Sent Stars invoice to chatId=$chatId via primary bot")
            true
        } catch (e: TelegramApiException) {
            logger.warn("Could not send invoice to chatId=$chatId via primary bot: ${e.message}")
            false
        }
    }
}
