package me.brightspark.mdcbot.extensions

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.User
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.gateway.Intent
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.defaultingDuration
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.extensions.event
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.utils.dm
import i18n.Translations
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toDateTimePeriod
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class DeleteSusFilesExtension : BaseExtension("delete-sus-files") {
	companion object {
		private const val MIME_JAR = "application/java-archive"
		private const val SUFFIX_JAR = ".jar"
	}

	private val jarFileAllow: MutableMap<JarFileAllowKey, JarFileAllowEntry> = mutableMapOf()

	private fun isUserJarFileAllowedAndRemove(user: User?, channelId: Snowflake): Boolean =
		user != null && jarFileAllow.remove(JarFileAllowKey(user.id, channelId))
			?.takeIf { it.expiry > Clock.System.now().epochSeconds } != null

	private fun putUserJarFileAllowed(userId: Snowflake, channelId: Snowflake, expiry: Long) {
		jarFileAllow[JarFileAllowKey(userId, channelId)] = JarFileAllowEntry(expiry)
	}

	override suspend fun setup() {
		intents(Intent.GuildMessages)

		event<MessageCreateEvent> {
			check { isNotBot() }
			check { botHasPermissions(Permission.ManageChannels) }
			action {
				val message = event.message
				val author = message.author
				if (isUserJarFileAllowedAndRemove(author, message.channelId))
					return@action // Allow user to post

				val jarFiles =
					message.attachments.filter { it.contentType == MIME_JAR || it.filename.endsWith(SUFFIX_JAR) }
				if (jarFiles.isNotEmpty()) {
					message.delete("Message contained JAR file attachments")
					message.author?.let { user ->
						user.dm {
							content = Translations.SusFile.dm.translate(user.mention, message.channel.mention)
						} ?: message.channel.createMessage {
							content = Translations.SusFile.channelMessage.translate(user.mention)
						}
					}
				}
			}
		}

		publicSlashCommand(::AllowJarArguments) {
			name = Translations.SusFile.Command.Allow.name
			description = Translations.SusFile.Command.Allow.desc
			userRequiresModeratorPermission()

			action {
				val targetUser = arguments.user
				val expiryTimestamp = Clock.System.now().plus(arguments.expiry, TimeZone.UTC).epochSeconds
				putUserJarFileAllowed(targetUser.id, channel.id, expiryTimestamp)
				respond {
					content = Translations.SusFile.Command.Allow.message.translate(
						targetUser.mention,
						expiryTimestamp.toString()
					)
				}
			}
		}
	}

	private class AllowJarArguments : Arguments() {
		val user by user {
			name = Translations.SusFile.Command.Allow.Arg.User.name
			description = Translations.SusFile.Command.Allow.Arg.User.desc
		}
		val expiry by defaultingDuration {
			name = Translations.SusFile.Command.Allow.Arg.Expiry.name
			description = Translations.SusFile.Command.Allow.Arg.Expiry.desc
			defaultValue = 10.minutes.toDateTimePeriod()
		}
	}

	private data class JarFileAllowKey(val userId: Snowflake, val channelId: Snowflake)

	private class JarFileAllowEntry(val expiry: Long)
}
