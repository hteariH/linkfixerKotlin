package com.mamoru.service

import com.mamoru.entity.UserBalance
import com.mamoru.repository.UserBalanceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

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
        userBalanceRepository.findById(userId).map { it.starBalance }.orElse(0)

    fun hasEnoughBalance(userId: Long): Boolean =
        getBalance(userId) >= COST_PER_MESSAGE

    fun deductStars(userId: Long, amount: Int = COST_PER_MESSAGE) {
        val current = userBalanceRepository.findById(userId).orElse(UserBalance(userId, 0))
        val newBalance = maxOf(0, current.starBalance - amount)
        userBalanceRepository.save(current.copy(starBalance = newBalance))
        logger.info("Deducted $amount ⭐ from userId=$userId, balance: ${current.starBalance} → $newBalance")
    }

    fun addStars(userId: Long, amount: Int) {
        val current = userBalanceRepository.findById(userId).orElse(UserBalance(userId, 0))
        val newBalance = current.starBalance + amount
        userBalanceRepository.save(current.copy(starBalance = newBalance))
        logger.info("Added $amount ⭐ to userId=$userId, balance: ${current.starBalance} → $newBalance")
    }

    /**
     * Sends a Stars invoice via the primary bot (HydraManagerBot).
     * Strategy:
     *  1. Try sending to current chat via primary bot (if primary bot is in the chat).
     *  2. Try sending to user's private chat via primary bot (if user has started it).
     *  3. Fall back: send a text message via the current bot with a deep link.
     *
     * [callerBot] is the bot that triggered the balance check (may be a managed bot).
     * [chatId] is the chat where the interaction happened.
     * [userId] is the user who triggered the bot.
     * [replyToMessageId] is the message to reply to in the current chat.
     */
    fun sendStarInvoice(
        callerBot: TelegramLongPollingBot,
        chatId: Long,
        userId: Long,
        replyToMessageId: Int
    ) {
        val primaryBot = primaryBotHolder.bot
        val primaryBotName = primaryBotHolder.botName

        if (primaryBot == null) {
            // Primary bot not yet initialized — should not normally happen
            logger.warn("Primary bot not set in PrimaryBotHolder, cannot send invoice")
            return
        }

        // 1. Try to send invoice to the current chat via the primary bot
        if (sendInvoice(primaryBot, chatId.toString(), replyToMessageId)) return

        // 2. Try to send invoice to the user's private chat via the primary bot
        if (sendInvoice(primaryBot, userId.toString(), null)) return

        // 3. Fallback: instruct user to top up via the primary bot
        val link = if (primaryBotName != null) "https://t.me/$primaryBotName" else "the main bot"
        try {
            val msg = SendMessage()
            msg.chatId = chatId.toString()
            msg.replyToMessageId = replyToMessageId
            msg.text = "У тебя недостаточно звёзд ⭐. " +
                "Пополни баланс через @$primaryBotName (${COST_PER_MESSAGE} ⭐ за сообщение)."
            callerBot.execute(msg)
        } catch (e: TelegramApiException) {
            logger.error("Failed to send fallback message to chatId=$chatId: ${e.message}", e)
        }
    }

    private fun sendInvoice(bot: TelegramLongPollingBot, chatId: String, replyToMessageId: Int?): Boolean {
        return try {
            val invoice = SendInvoice()
            invoice.chatId = chatId
            invoice.title = "Пополнение баланса ⭐"
            invoice.description = "100 звёзд для общения с ботом (${COST_PER_MESSAGE} ⭐ за сообщение)"
            invoice.payload = INVOICE_PAYLOAD
            invoice.providerToken = ""
            invoice.currency = "XTR"
            invoice.prices = listOf(LabeledPrice("100 звёзд", TOP_UP_AMOUNT))
            if (replyToMessageId != null) invoice.replyToMessageId = replyToMessageId
            bot.execute(invoice)
            logger.info("Sent Stars invoice to chatId=$chatId via primary bot")
            true
        } catch (e: TelegramApiException) {
            logger.warn("Could not send invoice to chatId=$chatId via primary bot: ${e.message}")
            false
        }
    }
}
