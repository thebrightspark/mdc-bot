package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.role
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Role
import dev.kord.core.entity.channel.Channel
import me.brightspark.mdcbot.model.PropertyName
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.service.PropertyService
import me.brightspark.mdcbot.util.toSimpleString
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class ConfigurationExtension(
	private val loggingService: LoggingService,
	private val mdcGuildId: Snowflake,
	private val propertyService: PropertyService
) : BaseExtension(propertyService) {
	private val log = KotlinLogging.logger {}

	override val name: String = "configuration"

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "admin"
			description = "Admin commands for configuring the bot"
			guild(mdcGuildId)
			check { isAdmin() }

			ephemeralSubCommand(::LogArguments) {
				name = "log"
				description = "Sets the log channel for the bot"

				action {
					val channel = arguments.channel
					propertyService.set(PropertyName.CHANNEL_LOGS, channel.id.toString())

					respond { content = "Log channel has been set to ${channel.mention}" }
					log.info { "Command log: Log channel set to ${channel.toSimpleString()}" }
				}
			}

			ephemeralSubCommand(::AdminRoleArguments) {
				name = "role"
				description = "Sets the role which is allowed to use the admin commands"

				action {
					val role = arguments.role
					propertyService.set(PropertyName.ROLE_ADMIN, role.id.toString())

					loggingService.log("Bot admin role set to ${role.mention}")
					respond { content = "Bot admin role has been set to ${role.mention}" }
					log.info { "Command adminRole: Admin role set to ${role.toSimpleString()}" }
				}
			}

			ephemeralSubCommand(::DevCategoryArguments) {
				name = "category"
				description = "Sets the developer category, where all developer channels will be created under"

				action {
					val category = arguments.category
					propertyService.set(PropertyName.CATEGORY_DEV, category.id.toString())

					val name = category.data.name.value
					loggingService.log("Dev category set to `$name`")
					respond { content = "Dev category has been set to `$name`" }
					log.info { "Command devCategory: Dev category set to ${category.toSimpleString()}" }
				}
			}
		}
	}

	inner class LogArguments : Arguments() {
		val channel: Channel by channel {
			name = "channel"
			description = "The channel"
			requireSameGuild = true
			requireChannelType(ChannelType.GuildText)
		}
	}

	inner class AdminRoleArguments : Arguments() {
		val role: Role by role {
			name = "role"
			description = "The role"
		}
	}

	inner class DevCategoryArguments : Arguments() {
		val category: Channel by channel {
			name = "category"
			description = "The category"
			requireSameGuild = true
			requireChannelType(ChannelType.GuildCategory)
		}
	}
}
