package me.brightspark.mdcbot.extensions

import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.GuildBehavior
import dev.kord.core.behavior.UserBehavior
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommand
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.optionalChannel
import dev.kordex.core.commands.converters.impl.optionalRole
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.types.EphemeralInteractionContext
import i18n.Translations
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.service.PropertyService
import me.brightspark.mdcbot.util.respondEmbed
import me.brightspark.mdcbot.util.respondSimple

class ConfigurationExtension : BaseExtension("configuration") {
	override suspend fun setup() {
		ephemeralSlashCommand {
			name = Translations.Config.Command.name
			description = Translations.Config.Command.desc
			mdcGuild()
			userRequiresAdminPermission()

			ephemeralSubCommand {
				name = Translations.Config.Command.List.name
				description = Translations.Config.Command.List.desc

				action {
					respondEmbed {
						Property.ALL.forEach { prop ->
							field(prop.nameTranslated, true) {
								val rawValue = PropertyService.getRaw(prop)
								val value = prop.deserialiseToDisplayString(guild!!, rawValue)
								Translations.Config.Command.List.Response.success.translate(value, rawValue)
							}
						}
					}
				}
			}

			ephemeralSubCommand(::ChannelLogsArguments) {
				setupPropertyCommand(Property.CHANNEL_LOGS) { it.channel?.id }
			}

			ephemeralSubCommand(::CategoryDevArguments) {
				setupPropertyCommand(Property.CATEGORY_DEV) { it.category?.id }
			}

			ephemeralSubCommand(::RoleModeratorArguments) {
				setupPropertyCommand(Property.ROLE_MODERATOR) { it.role?.id }
			}

//			ephemeralSubCommand(::ChannelsAutoPublishArguments) {
//				setupPropertyCommand(Property.AUTO_PUBLISH_CHANNELS) { it.channels.map(Channel::id) }
//			}
		}
	}

	class ChannelLogsArguments : Arguments() {
		val channel by optionalChannel {
			name = Translations.Config.Command.Set.ChannelLogs.Arg.Channel.name
			description = Translations.Config.Command.Set.ChannelLogs.Arg.Channel.desc
			requireChannelType(ChannelType.GuildText)
		}
	}

	class CategoryDevArguments : Arguments() {
		val category by optionalChannel {
			name = Translations.Config.Command.Set.CategoryDev.Arg.Category.name
			description = Translations.Config.Command.Set.CategoryDev.Arg.Category.desc
			requireChannelType(ChannelType.GuildCategory)
		}
	}

	class RoleModeratorArguments : Arguments() {
		val role by optionalRole {
			name = Translations.Config.Command.Set.RoleModerator.Arg.Role.name
			description = Translations.Config.Command.Set.RoleModerator.Arg.Role.desc
		}
	}

	// FIXME: This doesn't work atm - the value converter throws an error on use
//	class ChannelsAutoPublishArguments : Arguments() {
//		val channels by channelList {
//			name = Translations.Config.Command.Set.AutoPublish.Arg.Channels.name
//			description = Translations.Config.Command.Set.AutoPublish.Arg.Channels.desc
//			requireChannelType(ChannelType.GuildText)
//		}
//	}

	private fun <ARGS : Arguments, T> EphemeralSlashCommand<ARGS, ModalForm>.setupPropertyCommand(
		property: Property<T>,
		argValueGetter: (ARGS) -> T
	) {
		name = property.name
		description = Translations.Config.Command.Set.desc.withOrdinalPlaceholders(property.description)
		action {
			setProperty(guild!!, user, argValueGetter(arguments), property)
		}
	}

	private suspend fun <T> EphemeralInteractionContext.setProperty(
		guild: GuildBehavior,
		user: UserBehavior,
		value: T,
		property: Property<T>
	) {
		PropertyService.set(property, value)
		val selectedReadable = property.deserialiseToDisplayString(guild, value?.toString())
		LoggingService.log(
			"${property.longNameTranslated} has been set to $selectedReadable (Raw: $value)",
			user.asUser()
		)
		log.info { "Config ${property.id}: Set to $value" }
		respondSimple("${property.longNameTranslated} has been set to $selectedReadable")
	}
}
