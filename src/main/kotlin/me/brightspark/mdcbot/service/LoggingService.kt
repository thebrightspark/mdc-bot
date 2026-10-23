package me.brightspark.mdcbot.service

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kordex.core.utils.getKoin
import kotlinx.coroutines.launch
import me.brightspark.mdcbot.COROUTINE_SCOPE
import me.brightspark.mdcbot.Config
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.util.toSimpleString
import kotlin.time.Clock

object LoggingService {
	private val logChannel: Snowflake?
		get() = PropertyService.get(Property.CHANNEL_LOGS)

	fun log(message: String, user: User? = null) {
		logChannel?.let { logChannelId ->
			COROUTINE_SCOPE.launch {
				getKoin().get<Kord>().getGuildOrNull(Config.guildId)?.getChannel(logChannelId)
					?.takeIf { it is GuildMessageChannel }
					?.let {
						(it as GuildMessageChannel).createEmbed {
							description = message
							timestamp = Clock.System.now()
							user?.let { u ->
								footer {
									text = u.toSimpleString()
									icon = (u.avatar ?: u.defaultAvatar).cdnUrl.toUrl()
								}
							}
						}
					}
			}
		}
	}
}
