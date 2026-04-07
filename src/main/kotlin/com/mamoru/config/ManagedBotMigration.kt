package com.mamoru.config

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

@Component
class ManagedBotMigration(private val mongoTemplate: MongoTemplate) {

    private val logger = LoggerFactory.getLogger(ManagedBotMigration::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Order(1)
    fun migrate() {
        val collection = "managed_bots"
        val query = Query(Criteria.where("botToken").exists(true))
        val oldDocs = mongoTemplate.find(query, Document::class.java, collection)

        if (oldDocs.isEmpty()) return

        logger.info("Migrating {} managed_bot document(s) from token-based to botId-based schema", oldDocs.size)

        var migrated = 0
        for (doc in oldDocs) {
            val token = doc.getString("botToken") ?: continue
            val botId = token.substringBefore(":").toLongOrNull()
            if (botId == null) {
                logger.warn("Could not parse botId from token for document {}", doc["_id"])
                continue
            }
            mongoTemplate.updateFirst(
                Query(Criteria.where("_id").`is`(doc["_id"])),
                Update().set("botId", botId).unset("botToken"),
                collection
            )
            migrated++
            logger.info("Migrated @{}: extracted botId={}", doc.getString("botUsername"), botId)
        }

        logger.info("Migration complete: {}/{} documents updated", migrated, oldDocs.size)
    }
}
