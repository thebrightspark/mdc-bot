package me.brightspark.mdcbot.database.table

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

object BotPropertyTable : IdTable<String>("BotProperty") {
	override val id: Column<EntityID<String>> = varchar("id", 64).entityId()
	val value = text("prop_value").nullable().default(null)
}

class BotPropertyEntity(id: EntityID<String>) : Entity<String>(id) {
	companion object : EntityClass<String, BotPropertyEntity>(BotPropertyTable)

	var value by BotPropertyTable.value
}
