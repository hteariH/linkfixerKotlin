package com.mamoru.service

import com.mamoru.entity.UserBalance
import com.mamoru.repository.UserBalanceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

@Service
class StarBalanceService(
    private val userBalanceRepository: UserBalanceRepository
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

    fun sendStarInvoice(bot: TelegramLongPollingBot, chatId: Long, replyToMessageId: Int) {
        val invoice = SendInvoice()
        invoice.chatId = chatId.toString()
        invoice.title = "Пополнение баланса ⭐"
        invoice.description = "100 звёзд для общения с ботом (7 ⭐ за сообщение)"
        invoice.payload = INVOICE_PAYLOAD
        invoice.providerToken = ""
        invoice.currency = "XTR"
        invoice.prices = listOf(LabeledPrice("100 звёзд", TOP_UP_AMOUNT))
        invoice.replyToMessageId = replyToMessageId
        try {
            bot.execute(invoice)
            logger.info("Sent Stars invoice to chatId=$chatId")
        } catch (e: TelegramApiException) {
            logger.error("Failed to send Stars invoice to chatId=$chatId: ${e.message}", e)
        }
    }
}
