package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.DISCORD_RED
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.getJumpUrl
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Permission
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.Invite
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.brightspark.mdcbot.properties.Property
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.text.NumberFormat

@Component
class AutoModerationExtension : BaseExtension("auto-moderation") {
	companion object {
		private val REGEX_INVITE_LINK = Regex("(?:https?://)?discord(?:app)?\\.(?:(?:gg|com)/invite|gg)/(\\w+)")
		private val NUM_FORMAT = NumberFormat.getNumberInstance()
	}

	private val log = KotlinLogging.logger {}

	@OptIn(PrivilegedIntent::class)
	override suspend fun setup() {
		intents(Intent.GuildMessages, Intent.MessageContent)

		event<MessageCreateEvent> {
			check { isNotBot() }
			check { botHasPermissions(Permission.ManageMessages) }
			checkIf {
				!propContainsChannelOrParentCategory(Property.MODERATE_INVITES_CHANNEL_IGNORE, event.message.channel)
			}
			checkIf { event.member?.let { !it.isModerator() } ?: false }
			action { handleInviteLinks() }
		}
	}

	private suspend fun EventContext<MessageCreateEvent>.handleInviteLinks() {
		val message = event.message
		val inviteLinks = REGEX_INVITE_LINK.findAll(message.content)
			.map { InviteLinkInfo(it.value, it.groupValues[1]) }
			.toList()
		if (inviteLinks.isEmpty()) return
		val messageAuthor = message.author?.mention

		log.info { "Found invite links in message ${message.id}: ${inviteLinks.joinToString { it.code }}" }
		message.delete("Message contained ${inviteLinks.size} Discord server invite links")

		inviteLinks.map { this@AutoModerationExtension.kord.launch { it.retrieveInvite() } }.joinAll()
		val moderatorRole = propertyService.get(Property.ROLE_MODERATOR)?.let { "<@&$it>" } ?: "Moderator"

		val logsChannelMention = propertyService.get(Property.CHANNEL_LOGS)?.let { "<#$it>" }
		val responseMessage = message.channel.createMessage {
			embed {
				description = """
					A message sent by $messageAuthor has been deleted because it contained Discord server invite links.
					A $moderatorRole can use the buttons below to handle this further${logsChannelMention?.let { " (see logs for more info: $it)" } ?: ""}.
				""".trimIndent()
				color = DISCORD_RED
			}
			components {
				publicButton {
					label = "Send Links"
					style = ButtonStyle.Primary
					check { failIfNot { event.interaction.getMember().isModerator() } }
					action {
						this.channel.createMessage {
							content = """
								*Discord server invites originally posted by $messageAuthor:*
								${inviteLinks.distinctBy { it.code }.joinToString("\n") { "- ${it.link}" }}
							""".trimIndent()
						}
						this.message.delete()
					}
				}
				publicButton {
					label = "Send Original"
					style = ButtonStyle.Primary
					check { failIfNot { event.interaction.getMember().isModerator() } }
					action {
						this.channel.createMessage {
							content = """
								*Message originally posted by $messageAuthor:*

								${message.content}
							""".trimIndent()
						}
						this.message.delete()
					}
				}
				publicButton {
					label = "Delete"
					style = ButtonStyle.Danger
					check { failIfNot { event.interaction.getMember().isModerator() } }
					action { this.message.delete() }
				}
			}
		}

		val invitesString = inviteLinks.distinctBy { it.code }.joinToString("\n") { inviteInfo ->
			inviteInfo.invite?.let { invite ->
				val sb = StringBuilder()
				sb.append("|- ").append(inviteInfo.link).append("\n")
				invite.partialGuild?.let { g ->
					sb.append("| ").append(g.name).append(" (`").append(g.id).append("`)\n")
					sb.append("| Members ~").append(NUM_FORMAT.format(invite.approximateMemberCount))
						.append(" (Online ~").append(NUM_FORMAT.format(invite.approximatePresenceCount)).append(")\n")
					sb.append("| Channel ").append(invite.channel?.mention)
						.append(" (`").append(invite.channelId).append("`)\n")
				}
				sb.append("| Expiration: ")
				invite.expiresAt?.let { sb.append("<t:").append(it.epochSeconds).append(":f>") } ?: sb.append("None")
				sb.toString()
			} ?: "|- INVALID: ${inviteInfo.link}"
		}
		loggingService.log(
			"""
			|Deleted message from $messageAuthor in ${message.channel.mention}:
			|${responseMessage.getJumpUrl()}
			$invitesString
			""".trimMargin("|")
		)
	}

	private inner class InviteLinkInfo(val link: String, val code: String) {
		var invite: Invite? = null

		suspend fun retrieveInvite() {
			invite = this@AutoModerationExtension.kord.getInviteOrNull(code, withCounts = true, withExpiration = false)
		}
	}
}
