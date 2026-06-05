package com.mamoru.repository

import com.mamoru.entity.UserBalance
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

/**
 * Custom-fragment implementation for [UserBalanceRepositoryCustom]. Spring Data wires this
 * into [UserBalanceRepository] automatically via the `Impl` naming convention.
 */
class UserBalanceRepositoryImpl(
    private val mongoTemplate: MongoTemplate
) : UserBalanceRepositoryCustom {

    override fun deductIfSufficient(userId: Long, amount: Int, initialBalance: Int): Boolean {
        seed(userId, initialBalance)
        val updated = mongoTemplate.findAndModify(
            Query(Criteria.where("_id").`is`(userId).and("starBalance").gte(amount)),
            Update().inc("starBalance", -amount),
            FindAndModifyOptions.options().returnNew(true),
            UserBalance::class.java
        )
        return updated != null
    }

    override fun incrementBalance(userId: Long, amount: Int, initialBalance: Int): Int {
        seed(userId, initialBalance)
        val updated = mongoTemplate.findAndModify(
            Query(Criteria.where("_id").`is`(userId)),
            Update().inc("starBalance", amount),
            FindAndModifyOptions.options().returnNew(true),
            UserBalance::class.java
        )!!
        return updated.starBalance
    }

    /**
     * Idempotently create the balance document with [initialBalance]. `$setOnInsert` only
     * writes on insert, so concurrent callers race safely and existing balances are left
     * untouched — preserving the "new user gets a free starter balance" semantics.
     */
    private fun seed(userId: Long, initialBalance: Int) {
        mongoTemplate.upsert(
            Query(Criteria.where("_id").`is`(userId)),
            Update().setOnInsert("starBalance", initialBalance),
            UserBalance::class.java
        )
    }
}
