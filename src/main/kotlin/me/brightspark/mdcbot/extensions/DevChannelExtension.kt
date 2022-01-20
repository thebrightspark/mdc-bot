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
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.createTextChannel
import dev.kord.core.behavior.channel.edit
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.channel.CategoryDeleteEvent
import dev.kord.core.event.channel.TextChannelDeleteEvent
import dev.kord.core.event.interaction.ApplicationInteractionCreateEvent
import dev.kord.rest.builder.message.create.embed
import me.brightspark.mdcbot.database.model.DevChannel
import me.brightspark.mdcbot.database.service.DevChannelService
import me.brightspark.mdcbot.model.PropertyName
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.service.PropertyService
import me.brightspark.mdcbot.util.toSimpleString
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class DevChannelExtension(
	private val devChannelService: DevChannelService,
	private val loggingService: LoggingService,
	private val mdcGuildId: Snowflake,
	private val propertyService: PropertyService
) : BaseExtension(propertyService) {
	private val log = KotlinLogging.logger {}

	override val name: String = "slash-commands"

	override suspend fun setup() {
		event<CategoryDeleteEvent> {
			check {
				failIfNot {
					propertyService.get(PropertyName.CATEGORY_DEV)?.let {
						event.channel.id.toString() == it
					} ?: false
				}
			}
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
			guild(mdcGuildId)

			publicSubCommand(::CreateDevChannelArguments) {
				name = "create"
				description = "Creates a new developer channel for the given server member"
				requireBotPermissions(Permission.ManageChannels)
				check { isAdmin() } // TODO: Later allow this to be used by devs

				action {
					val member = arguments.member ?: event.interaction.getMember()
					arguments.name?.let { createDevChannel(member, it) } ?: createDevChannel(member)
				}
			}

			publicSubCommand(::SetNameArguments) {
				name = "rename"
				description = "Sets your dev channel's name"
				check { ownsADevChannel() }

				action {
					val user = event.interaction.user
					val channelId = devChannelService.getByUserId(user.id.value.toLong())!!.channelId
					val channel = event.interaction.kord.getChannel(Snowflake(channelId))
						?.takeIf { it is TextChannel }?.let { it as TextChannel }
						?: run {
							// Channel does not exist!
							respond { content = "Your dev channel does not exist!" }
							return@action
						}

					val oldName = channel.name
					val newName = channel.edit {
						name = arguments.name
						reason = "Renamed by ${user.mention}"
					}.name

					loggingService.log("Renamed channel $channelId from `$oldName` to `$newName`")
					respond { content = "Renamed your channel to `$newName`" }
					log.info { "Command dev name: Renamed channel $channelId from '$oldName' to '$newName'" }
				}
			}
		}

		publicUserCommand {
			name = "Create dev channel"
			guild(mdcGuildId)
			check { isAdmin() }

			action {
				val member = event.interaction.user.asMember(event.interaction.data.guildId.value!!)
				createDevChannel(member)
			}
		}
	}

	private suspend fun PublicInteractionContext.createDevChannel(
		member: Member,
		name: String = "${member.displayName}-mods"
	) {
		// Check if the member already owns a channel
		devChannelService.getByUserId(member.id.value.toLong())?.let { devChannel ->
			kord.getChannel(Snowflake(devChannel.channelId))?.let { channel ->
				// Channel already exists!
				respond { content = "A channel already exists for ${member.mention} -> ${channel.mention}" }
				return
			} ?: run {
				// Channel stored doesn't exist!
				log.warn { "Command createDevChannel: DevChannel for member ${member.id} has invalid channel ${devChannel.channelId}... removing existing data" }
				devChannelService.remove(devChannel)
			}
		}

		val categoryId = propertyService.get(PropertyName.CATEGORY_DEV) ?: run {
			respond { content = "The dev category has not been set!" }
			log.warn { "Command createDevChannel: Dev category has not been set" }
			return
		}
		val category = kord.getChannel(Snowflake(categoryId))
			?.takeIf { it is Category }?.let { it as Category }
			?: run {
				respond { content = "The dev category could not be found!" }
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
		val botMessage = channel.createMessage {
			embed {
				description = "**Owner:** ${member.mention}"
			}
		}.apply { pin("Dev channel bot message") }

		devChannelService.save(
			DevChannel(channel.id.value.toLong(), member.id.value.toLong(), botMessage.id.value.toLong())
		)

		loggingService.log("New dev channel ${channel.mention} created, owned by ${member.mention}")
		respond { content = "New dev channel ${channel.mention} created, owned by ${member.mention}" }
		log.info { "Command createDevChannel: Created new dev channel ${channel.id} for member ${member.toSimpleString()}" }
	}

	private suspend fun CheckContext<ApplicationInteractionCreateEvent>.ownsADevChannel() {
		failIfNot {
			return@failIfNot devChannelService.getByUserId(event.interaction.user.id.value.toLong()) != null
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

	inner class SetNameArguments : Arguments() {
		val name: String by string {
			name = "name"
			description = "The dev channel name"
		}
	}

	inner class DeleteDevChannelArguments : Arguments() {

	}
}
