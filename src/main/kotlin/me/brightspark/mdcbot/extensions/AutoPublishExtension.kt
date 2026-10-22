package me.brightspark.mdcbot.extensions

import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.NewsChannelBehavior
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.Intent
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.event
import io.github.oshai.kotlinlogging.KotlinLogging
import me.brightspark.mdcbot.properties.Property
import org.springframework.stereotype.Component

// TODO: Disabled for now, since the config command for it doesn't work anyways. Plus idk if I want this going forward.
//@Component
class AutoPublishExtension : BaseExtension("auto-publish") {
	private val log = KotlinLogging.logger {}

	override suspend fun setup() {
		intents(Intent.GuildMessages)

		event<MessageCreateEvent> {
			check { isNotBot() }
			check { botHasPermissions(Permission.ManageChannels) }
			checkIf { event.message.channel is NewsChannelBehavior }
			checkIf { propContainsChannelOrParentCategory(Property.AUTO_PUBLISH_CHANNELS, event.message.channel) }
			action {
				val message = event.message
				log.info { "Auto-publishing message ${message.id} in channel ${message.channel.id}" }
				message.publish()
			}
		}
	}
}
