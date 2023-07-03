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
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.util.toSimpleString
import org.springframework.stereotype.Service

@Service
class LoggingService(
	private val coroutineScope: CoroutineScope,
	private val mdcGuildId: Snowflake,
	private val propertyService: PropertyService
) {
	private val logChannel: Snowflake?
		get() = propertyService.get(Property.CHANNEL_LOGS)

	fun log(message: String, user: User? = null) {
		logChannel?.let { logChannelId ->
			coroutineScope.launch {
				getKoin().get<Kord>().getGuildOrNull(mdcGuildId)?.getChannel(logChannelId)
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
