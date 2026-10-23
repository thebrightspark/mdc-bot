package me.brightspark.mdcbot.database.service

import me.brightspark.mdcbot.database.findOrCreate
import me.brightspark.mdcbot.database.table.BotPropertyEntity
import me.brightspark.mdcbot.database.updateOrCreate
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object BotPropertyService {
	fun get(propertyId: String): String? = transaction {
		findOrCreate(BotPropertyEntity, propertyId).value
	}

	fun put(propertyId: String, propertyValue: String?): Unit = transaction {
		updateOrCreate(BotPropertyEntity, propertyId) { it.value = propertyValue }
	}
}
