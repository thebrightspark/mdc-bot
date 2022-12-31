package me.brightspark.mdcbot.service

import com.kotlindiscord.kord.extensions.utils.getKoin
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.GuildMessageChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.brightspark.mdcbot.model.PropertyName
import org.springframework.stereotype.Service

@Service
class LoggingService(
	private val coroutineScope: CoroutineScope,
	private val mdcGuildId: Snowflake,
	private val propertyService: PropertyService
) {
	private val logChannel: Snowflake?
		get() = propertyService.get(PropertyName.CHANNEL_LOGS)?.let { Snowflake(it) }

	fun log(message: String, user: User? = null) {
		logChannel?.let {
			coroutineScope.launch {
				val channel = getKoin().get<Kord>().getGuildOrNull(mdcGuildId)?.getChannel(it)
				if (channel is GuildMessageChannel)
					channel.createEmbed {
						description = message
						timestamp = Clock.System.now()
						user?.let { u ->
							footer {
								text = "${u.username}#${u.discriminator} (${u.id})"
								icon = u.avatar?.url ?: u.defaultAvatar.url
							}
						}
					}
			}
		}
	}
}
