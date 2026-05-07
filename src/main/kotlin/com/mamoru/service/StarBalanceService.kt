package com.mamoru.service

import com.mamoru.entity.UserBalance
import com.mamoru.repository.UserBalanceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient

@Service
class StarBalanceService(
    private val userBalanceRepository: UserBalanceRepository,
    private val primaryBotHolder: PrimaryBotHolder
) {
    private val logger = LoggerFactory.getLogger(StarBalanceService::class.java)

    companion object {
        const val COST_PER_MESSAGE = 3
        const val AGENT_COST = 39

        /** callback_data suffix → amount in stars */
        val TOP_UP_OPTIONS = linkedMapOf(
            "topup:45" to 45,
            "topup:150" to 150,
            "topup:450" to 450
        )

        /** Telegram invoice payload for a given callback key */
        fun payloadFor(callbackData: String): String? =
            TOP_UP_OPTIONS[callbackData]?.let { "stars_topup_$it" }

        val INVOICE_PAYLOADS: Set<String> =
            TOP_UP_OPTIONS.values.map { "stars_topup_$it" }.toSet()

        fun promptInsufficientBalance(cost: Int) = "У тебя недостаточно звёзд ⭐ для ответа бота ($cost ⭐ за сообщение).\nВыбери сколько пополнить:"
        fun promptTopUp(cost: Int, primaryBotName: String) = "У тебя недостаточно звёзд ⭐. Пополни баланс через @$primaryBotName ($cost ⭐ за сообщение)."
    }

    fun getBalance(userId: Long): Int =
        userBalanceRepository.findById(userId).map { it.starBalance }.orElse(COST_PER_MESSAGE)

    fun hasEnoughBalance(userId: Long): Boolean =
        getBalance(userId) >= COST_PER_MESSAGE

    fun deductStars(userId: Long, amount: Int) {
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
     * Sends a message with three inline buttons (5 / 50 / 500 ⭐) via the primary bot.
     * Strategy:
     *  1. Try sending to the current chat via primary bot.
     *  2. Try sending to user's private chat via primary bot.
     *  3. Fallback: plain text via caller's client.
     */
    fun sendStarInvoice(
        callerClient: TelegramClient,
        chatId: Long,
        userId: Long,
        replyToMessageId: Int,
        cost: Int = COST_PER_MESSAGE
    ) {
        val primaryClient = primaryBotHolder.client
        val primaryBotName = primaryBotHolder.botName

        if (primaryClient == null) {
            logger.warn("Primary bot client not set in PrimaryBotHolder, cannot send invoice selector")
            return
        }

        // 1. Try current chat
        if (sendSelectionMessage(primaryClient, chatId.toString(), replyToMessageId, cost)) return

        // 2. Try user's private chat
        if (sendSelectionMessage(primaryClient, userId.toString(), null, cost)) return

        // 3. Fallback: plain text
        try {
            val msg = SendMessage.builder()
                .chatId(chatId.toString())
                .replyToMessageId(replyToMessageId)
                .text(promptTopUp(cost, primaryBotName ?: "HydraManagerBot"))
                .build()
            callerClient.execute(msg)
        } catch (e: TelegramApiException) {
            logger.error("Failed to send fallback message to chatId=$chatId: ${e.message}", e)
        }
    }

    private fun sendSelectionMessage(
        client: TelegramClient,
        chatId: String,
        replyToMessageId: Int?,
        cost: Int
    ): Boolean {
        return try {
            val row = InlineKeyboardRow(
                TOP_UP_OPTIONS.entries.map { (callbackData, amount) ->
                    InlineKeyboardButton.builder()
                        .text("$amount ⭐")
                        .callbackData(callbackData)
                        .build()
                }
            )
            val keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build()

            val builder = SendMessage.builder()
                .chatId(chatId)
                .text(promptInsufficientBalance(cost))
                .replyMarkup(keyboard)
            if (replyToMessageId != null) builder.replyToMessageId(replyToMessageId)
            client.execute(builder.build())
            logger.info("Sent top-up selector to chatId=$chatId")
            true
        } catch (e: TelegramApiException) {
            logger.warn("Could not send top-up selector to chatId=$chatId: ${e.message}")
            false
        }
    }
}
