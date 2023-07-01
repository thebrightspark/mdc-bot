package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.NewsChannelBehavior
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.Intent
import me.brightspark.mdcbot.properties.Property
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class AutoPublishExtension : BaseExtension("auto-publish") {
	private val log = KotlinLogging.logger {}

	override suspend fun setup() {
		intents(Intent.GuildMessages)

		event<MessageCreateEvent> {
			check { isNotBot() }
			check { botHasPermissions(Permission.ManageChannels) }
			checkIf { event.message.channel is NewsChannelBehavior }
			checkIf {
				propertyService.get(Property.AUTO_PUBLISH_CHANNELS)
					?.contains(event.message.channel.id)
					?: false
			}
			action {
				val message = event.message
				log.info { "Auto-publishing message ${message.id} in channel ${message.channel.id}" }
				message.publish()
			}
		}
	}
}
