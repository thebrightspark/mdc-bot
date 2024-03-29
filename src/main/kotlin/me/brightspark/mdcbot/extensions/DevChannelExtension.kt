package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalMember
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.extensions.publicUserCommand
import com.kotlindiscord.kord.extensions.types.PublicInteractionContext
import com.kotlindiscord.kord.extensions.types.respond
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
import dev.kord.rest.builder.message.create.embed
import me.brightspark.mdcbot.database.model.DevChannel
import me.brightspark.mdcbot.database.service.DevChannelService
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.util.getCategory
import me.brightspark.mdcbot.util.respondSimple
import me.brightspark.mdcbot.util.toSimpleString
import mu.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.time.Duration

@Component
class DevChannelExtension(
	private val devChannelService: DevChannelService
) : BaseExtension("dev-channel") {
	private val log = KotlinLogging.logger {}

	override suspend fun setup() {
		intents(Intent.Guilds)

		event<CategoryDeleteEvent> {
			checkIf { propertyService.get(Property.CATEGORY_DEV).let { event.channel.id == it } }
			action {
				loggingService.log("Dev category ${event.channel.toSimpleString()} was deleted")
				log.info { "Dev category ${event.channel.toSimpleString()} deleted - removing all data" }
				devChannelService.removeAll()
			}
		}

		event<TextChannelDeleteEvent> {
			action {
				val channel = event.channel
				devChannelService.getByChannelId(channel.id.value.toLong())?.let {
					loggingService.log("Dev channel ${channel.toSimpleString()} was deleted")
					log.info { "Dev channel ${channel.toSimpleString()} deleted - removing data" }
					devChannelService.remove(it)
				}
			}
		}

		publicSlashCommand {
			name = "dev"
			description = "Commands for developers to use for creating and managing their personal channels"
			mdcGuild()

			publicSubCommand(::CreateDevChannelArguments) {
				name = "create"
				description = "Creates a new developer channel for the given server member"
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
				name = "rename"
				description = "Renames your dev channel"
				check { ownsADevChannel() }

				action {
					val user = event.interaction.user.asUser()
					val devChannel = devChannelService.getByUserId(user.id.value.toLong())!!
					val channelId = devChannel.channelId
					val channel = event.interaction.kord.getChannel(Snowflake(channelId))
						?.takeIf { it is TextChannel }?.let { it as TextChannel }
						?: run {
							// Channel does not exist!
							devChannelService.remove(devChannel)
							respondSimple("Your dev channel does not exist!")
							return@action
						}

					val oldName = channel.name
					val newName = channel.edit {
						name = arguments.name
						reason = "Renamed by ${user.toSimpleString()}"
					}.name

					loggingService.log("Renamed channel $channelId from `$oldName` to `$newName`", user)
					respondSimple("Renamed your channel to `$newName`")
					log.info { "Command dev rename: ${user.toSimpleString()} renamed channel $channelId from '$oldName' to '$newName'" }
				}
			}

			publicSubCommand {
				name = "invite"
				description = "Gets or creates an invite for your dev channel"
				check { ownsADevChannel() }

				action {
					val user = event.interaction.user.asUser()
					val devChannel = devChannelService.getByUserId(user.id.value.toLong())!!
					val kord = event.kord
					val channel = kord.getChannel(Snowflake(devChannel.channelId))!! as TextChannel
					val invite: Invite = devChannel.inviteCode?.let { inviteCode ->
						kord.getGuildOrNull(mdcGuildId)!!.getInviteOrNull(inviteCode, false)
					} ?: run {
						channel.createInvite {
							maxAge = Duration.ZERO // No expiration
							reason = "Invite for dev channel ${channel.toSimpleString()}"
						}.also {
							devChannel.inviteCode = it.code
							devChannelService.save(devChannel)
							loggingService.log(
								"Created new invite '${it.code}' for dev channel ${channel.toSimpleString()}",
								user
							)
							log.info { "Command dev invite: ${user.toSimpleString()} created new invite '${it.code}' for dev channel ${channel.toSimpleString()}" }
						}
					}

					respond {
						embed { description = "Invite to ${channel.mention}:\n`https://discord.gg/${invite.code}`" }
					}
				}
			}
		}

		publicUserCommand {
			name = "Create dev channel"
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
		devChannelService.getByUserId(member.id.value.toLong())?.let { devChannel ->
			kord.getChannel(Snowflake(devChannel.channelId))?.let { channel ->
				// Channel already exists!
				respond {
					embed { description = "A channel already exists for ${member.mention} -> ${channel.mention}" }
				}
				return
			} ?: run {
				// Channel stored doesn't exist!
				log.warn { "Command createDevChannel: DevChannel for member ${member.id} has invalid channel ${devChannel.channelId}... removing existing data" }
				devChannelService.remove(devChannel)
			}
		}

		val categoryId = propertyService.get(Property.CATEGORY_DEV) ?: run {
			respondSimple("The dev category has not been set!")
			log.warn { "Command createDevChannel: Dev category has not been set" }
			return
		}
		val category = kord.getCategory(categoryId) ?: run {
			respondSimple("The dev category could not be found!")
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

		devChannelService.save(
			DevChannel(channel.id.value.toLong(), member.id.value.toLong(), botMessage.id.value.toLong())
		)

		loggingService.log("New dev channel ${channel.mention} created, owned by ${member.mention}", creator)
		respondSimple("New dev channel ${channel.mention} created, owned by ${member.mention}")
		log.info { "Command createDevChannel: ${creator.toSimpleString()} created new dev channel ${channel.toSimpleString()} for member ${member.toSimpleString()}" }
	}

	private suspend fun CheckContext<ApplicationCommandInteractionCreateEvent>.ownsADevChannel() {
		failIfNot("You do not own a dev channel!") {
			return@failIfNot devChannelService.getByUserId(event.interaction.user.id.value.toLong())?.channelId != null
		}
	}

	inner class CreateDevChannelArguments : Arguments() {
		val member: Member? by optionalMember {
			name = "user"
			description = "The server member"
		}
		val name: String? by optionalString {
			name = "name"
			description = "The new channel name"
		}
	}

	inner class RenameArguments : Arguments() {
		val name: String by string {
			name = "name"
			description = "The dev channel name"
		}
	}
}
