package me.brightspark.mdcbot.model

import me.brightspark.mdcbot.database.table.DevChannelEntity

data class DevChannel(val channelId: ULong, val userId: ULong, val botMessageId: ULong?, val inviteCode: String?) {
	companion object {
		fun fromEntity(entity: DevChannelEntity): DevChannel =
			DevChannel(entity.id.value, entity.userId, entity.botMessageId, entity.inviteCode)
	}
}
