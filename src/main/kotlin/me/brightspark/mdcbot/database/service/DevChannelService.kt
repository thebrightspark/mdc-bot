package me.brightspark.mdcbot.database.service

import io.github.oshai.kotlinlogging.KotlinLogging
import me.brightspark.mdcbot.database.table.DevChannelEntity
import me.brightspark.mdcbot.database.table.DevChannelTable
import me.brightspark.mdcbot.database.updateOrCreate
import me.brightspark.mdcbot.model.DevChannel
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DevChannelService {
	private val log = KotlinLogging.logger {}

	fun create(channelId: ULong, userId: ULong, botMessageId: ULong): Unit = transaction {
		DevChannelEntity.new(channelId) {
			this.userId = userId
			this.botMessageId = botMessageId
		}
	}

	fun exists(channelId: ULong): Boolean = transaction {
		DevChannelTable.select(DevChannelTable.id)
			.where { DevChannelTable.id eq channelId }
			.limit(1)
			.count() > 0
	}

	fun getAll(): List<DevChannel> = transaction {
		DevChannelEntity.all().map(DevChannel::fromEntity)
	}

	fun getByChannelId(channelId: ULong): DevChannel? = transaction {
		DevChannelEntity.findById(channelId)?.let { DevChannel.fromEntity(it) }
	}

	fun getByUserId(userId: ULong): DevChannel? = transaction {
		DevChannelEntity.find { DevChannelTable.userId eq userId }.limit(1)
			.firstOrNull()
			?.let { DevChannel.fromEntity(it) }
	}

	fun setInvite(channelId: ULong, inviteCode: String): Unit = transaction {
		updateOrCreate(DevChannelEntity, channelId) { it.inviteCode = inviteCode }
	}

	fun remove(channelId: ULong) {
		transaction { DevChannelTable.deleteWhere { DevChannelTable.id eq channelId } }
		log.info { "remove: Removed entry $channelId" }
	}

	fun removeAll(): Int = transaction { DevChannelTable.deleteAll() }
		.also { log.info { "removeAll: Removed all $it entries" } }
}
