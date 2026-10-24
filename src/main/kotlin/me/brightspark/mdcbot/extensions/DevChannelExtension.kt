package me.brightspark.mdcbot.extensions

import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createInvite
import dev.kord.core.behavior.channel.createTextChannel
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.Invite
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.channel.CategoryDeleteEvent
import dev.kord.core.event.channel.TextChannelDeleteEvent
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import dev.kord.gateway.Intent
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalMember
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.publicUserCommand
import dev.kordex.core.types.PublicInteractionContext
import i18n.Translations
import me.brightspark.mdcbot.database.service.DevChannelService
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.service.PropertyService
import me.brightspark.mdcbot.util.getCategory
import me.brightspark.mdcbot.util.respondSimple
import me.brightspark.mdcbot.util.toSimpleString
import kotlin.time.Duration

class DevChannelExtension : BaseExtension("dev-channel") {
	override suspend fun setup() {
		intents(Intent.Guilds)

		event<CategoryDeleteEvent> {
			checkIf { PropertyService.get(Property.CATEGORY_DEV).let { event.channel.id == it } }
			action {
				LoggingService.log("Dev category ${event.channel.toSimpleString()} was deleted")
				log.info { "Dev category ${event.channel.toSimpleString()} deleted - removing all data" }
				DevChannelService.removeAll()
			}
		}

		event<TextChannelDeleteEvent> {
			action {
				val channel = event.channel
				DevChannelService.getByChannelId(channel.id.value)?.let {
					LoggingService.log("Dev channel ${channel.toSimpleString()} was deleted")
					log.info { "Dev channel ${channel.toSimpleString()} deleted - removing data" }
					DevChannelService.remove(it.id.value)
				}
			}
		}

		publicSlashCommand {
			name = Translations.Command.Dev.name
			description = Translations.Command.Dev.desc
			mdcGuild()

			publicSubCommand(::CreateDevChannelArguments) {
				name = Translations.Command.Dev.Create.name
				description = Translations.Command.Dev.Create.desc
				requireBotPermissions(Permission.ManageChannels)
				userRequiresModeratorPermission() // TODO: Later allow this to be used by devs

				action {
					val member = arguments.member ?: event.interaction.getMember()
					arguments.name
						?.let {
							createDevChannel(
								member = member,
								name = it,
								creator = event.interaction.user.asUser()
							)
						}
						?: createDevChannel(member = member, creator = event.interaction.user.asUser())
				}
			}

			publicSubCommand(::RenameArguments) {
				name = Translations.Command.Dev.Rename.name
				description = Translations.Command.Dev.Rename.desc
				check { ownsADevChannel() }

				action {
					val user = event.interaction.user.asUser()
					val devChannel = DevChannelService.getByUserId(user.id.value)!!
					val channelId = devChannel.id.value
					val channel = event.interaction.kord.getChannel(Snowflake(channelId))
						?.takeIf { it is TextChannel }?.let { it as TextChannel }
						?: run {
							// Channel does not exist!
							DevChannelService.remove(channelId)
							respondSimple(Translations.Command.Dev.Rename.Response.notExist)
							return@action
						}

					val oldName = channel.name
					val newName = channel.edit {
						name = arguments.name
						reason = "Renamed by ${user.toSimpleString()}"
					}.name

					LoggingService.log("Renamed channel $channelId from `$oldName` to `$newName`", user)
					respondSimple(Translations.Command.Dev.Rename.Response.success.withOrdinalPlaceholders(newName))
					log.info { "Command dev rename: ${user.toSimpleString()} renamed channel $channelId from '$oldName' to '$newName'" }
				}
			}

			publicSubCommand {
				name = Translations.Command.Dev.Invite.name
				description = Translations.Command.Dev.Invite.desc
				check { ownsADevChannel() }

				action {
					val user = event.interaction.user.asUser()
					val devChannel = DevChannelService.getByUserId(user.id.value)!!
					val kord = event.kord
					val channel = kord.getChannel(Snowflake(devChannel.id.value))!! as TextChannel
					val invite: Invite = devChannel.inviteCode?.let { inviteCode ->
						kord.getGuildOrNull(mdcGuildId)!!.getInviteOrNull(inviteCode, false)
					} ?: run {
						channel.createInvite {
							maxAge = Duration.ZERO // No expiration
							reason = "Invite for dev channel ${channel.toSimpleString()}"
						}.also {
							DevChannelService.setInvite(devChannel.id.value, it.code)
							LoggingService.log(
								"Created new invite '${it.code}' for dev channel ${channel.toSimpleString()}",
								user
							)
							log.info { "Command dev invite: ${user.toSimpleString()} created new invite '${it.code}' for dev channel ${channel.toSimpleString()}" }
						}
					}

					respondSimple(
						Translations.Command.Dev.Invite.Response.success
							.withOrdinalPlaceholders(channel.mention, "https://discord.gg/${invite.code}")
					)
				}
			}
		}

		publicUserCommand {
			name = Translations.UserCommand.Dev.name
			mdcGuild()
			userRequiresModeratorPermission()

			action {
				val member = event.interaction.getTarget().asMember(event.interaction.data.guildId.value!!)
				createDevChannel(member = member, creator = event.interaction.user.asUser())
			}
		}
	}

	private suspend fun PublicInteractionContext.createDevChannel(
		member: Member,
		name: String = "${member.effectiveName}-mods",
		creator: User
	) {
		// Check if the member already owns a channel
		DevChannelService.getByUserId(member.id.value)?.let { devChannel ->
			val devChannelId = devChannel.id.value
			kord.getChannel(Snowflake(devChannelId))?.let { channel ->
				// Channel already exists!
				respondSimple(
					Translations.Command.Dev.Create.Response.exists
						.withOrdinalPlaceholders(member.mention, channel.mention)
				)
				return
			} ?: run {
				// Channel stored doesn't exist!
				log.warn { "Command createDevChannel: DevChannel for member ${member.id} has invalid channel ${devChannelId}... removing existing data" }
				DevChannelService.remove(devChannelId)
			}
		}

		val categoryId = PropertyService.get(Property.CATEGORY_DEV) ?: run {
			respondSimple(Translations.Command.Dev.Create.Response.categoryNotSet)
			log.warn { "Command createDevChannel: Dev category has not been set" }
			return
		}
		val category = kord.getCategory(categoryId) ?: run {
			respondSimple(Translations.Command.Dev.Create.Response.categoryNotFound)
			log.warn { "Command createDevChannel: Dev category with ID $categoryId could not be found" }
			return
		}

		// Create new channel
		val channel = category.createTextChannel(name) {
			permissionOverwrites += Overwrite(
				member.id,
				OverwriteType.Member,
				Permissions(Permission.ManageMessages, Permission.ManageThreads),
				Permissions()
			)
		}

		// Send bot message in new channel
		val botMessage = channel.createEmbed { description = "**Owner:** ${member.mention}" }
			.apply { pin("Dev channel bot message") }

		DevChannelService.create(channel.id.value, member.id.value, botMessage.id.value)

		LoggingService.log("New dev channel ${channel.mention} created, owned by ${member.mention}", creator)
		respondSimple(
			Translations.Command.Dev.Create.Response.success.withOrdinalPlaceholders(channel.mention, member.mention)
		)
		log.info { "Command createDevChannel: ${creator.toSimpleString()} created new dev channel ${channel.toSimpleString()} for member ${member.toSimpleString()}" }
	}

	private suspend fun CheckContext<ApplicationCommandInteractionCreateEvent>.ownsADevChannel() {
		failIfNot(Translations.Check.Fail.Dev.none) {
			return@failIfNot DevChannelService.exists(event.interaction.user.id.value)
		}
	}

	class CreateDevChannelArguments : Arguments() {
		val member: Member? by optionalMember {
			name = Translations.Command.Dev.Create.Arg.Member.name
			description = Translations.Command.Dev.Create.Arg.Member.desc
		}
		val name: String? by optionalString {
			name = Translations.Command.Dev.Create.Arg.Name.name
			description = Translations.Command.Dev.Create.Arg.Name.desc
		}
	}

	class RenameArguments : Arguments() {
		val name: String by string {
			name = Translations.Command.Dev.Rename.Arg.Name.name
			description = Translations.Command.Dev.Rename.Arg.Name.desc
		}
	}
}
