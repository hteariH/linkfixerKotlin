package com.mamoru.repository

/**
 * Atomic balance operations backed by Mongo's single-document `findAndModify`.
 *
 * These are the safe equivalent of `SELECT … FOR UPDATE`: the read-modify-write happens
 * server-side in one operation, so there is no lost-update race in the JVM.
 */
interface UserBalanceRepositoryCustom {

    /**
     * Atomically decrement the balance by [amount], but only if it is at least [amount].
     * A brand-new user's document is seeded with [initialBalance] first.
     *
     * @return true if the stars were deducted, false if the balance was insufficient.
     */
    fun deductIfSufficient(userId: Long, amount: Int, initialBalance: Int): Boolean

    /**
     * Atomically increment the balance by [amount], seeding a brand-new user's document
     * with [initialBalance] first.
     *
     * @return the resulting balance.
     */
    fun incrementBalance(userId: Long, amount: Int, initialBalance: Int): Int
}
