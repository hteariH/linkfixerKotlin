package com.mamoru.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.mamoru.service.ManagedBotService
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.Update

class ManagedUpdateDeserializer(
    private val managedBotService: ManagedBotService
) : StdDeserializer<Update>(Update::class.java) {

    private val logger = LoggerFactory.getLogger(ManagedUpdateDeserializer::class.java)

    // Separate mapper with no custom deserializers — avoids infinite recursion
    private val baseMapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Update {
        val node = p.readValueAsTree<ObjectNode>()

        node.get("managed_bot")?.let { managedBotNode ->
            try {
                val botId = managedBotNode.get("bot")?.get("id")?.asLong()
                val botUsername = managedBotNode.get("bot")?.get("username")?.asText()
                if (botId != null && botUsername != null) {
                    logger.info("Received managed_bot update: @$botUsername (id=$botId)")
                    Thread {
                        managedBotService.handleManagedBotCreated(botId, botUsername)
                    }.also { it.isDaemon = true; it.start() }
                }
            } catch (e: Exception) {
                logger.error("Error handling managed_bot update: ${e.message}", e)
            }
        }
        node.remove("managed_bot")

        return baseMapper.treeToValue(node, Update::class.java)
    }
}
