package me.brightspark.mdcbot.database.table

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass

object DevChannelTable : IdTable<ULong>("DevChannel") {
	override val id: Column<EntityID<ULong>> = ulong("channelId").entityId()
	val userId = ulong("userId")
	val botMessageId = ulong("botMessageId").nullable()
	val inviteCode = varchar("inviteCode", 16).nullable()
}

class DevChannelEntity(id: EntityID<ULong>) : ULongEntity(id) {
	companion object : ULongEntityClass<DevChannelEntity>(DevChannelTable)

	var userId by DevChannelTable.userId
	var botMessageId by DevChannelTable.botMessageId
	var inviteCode by DevChannelTable.inviteCode
}
