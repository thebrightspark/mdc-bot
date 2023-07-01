package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.DISCORD_GREEN
import com.kotlindiscord.kord.extensions.DISCORD_YELLOW
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.utils.FilterStrategy
import com.kotlindiscord.kord.extensions.utils.suggestStringCollection
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.ForumTag
import dev.kord.core.behavior.channel.GuildChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.channel.threads.edit
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.ForumChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.interaction.AutoCompleteInteraction
import dev.kord.core.entity.interaction.ChannelOptionValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.brightspark.mdcbot.util.editSimple
import me.brightspark.mdcbot.util.respondSimple
import me.brightspark.mdcbot.util.toSimpleString
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
class ForumHelperExtension : BaseExtension("forum-helper") {
	private val log = KotlinLogging.logger {}

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "forum"
			description = "Commands for managing forum channels"
			mdcGuild()
			userRequiresModeratorPermission()

			ephemeralSubCommand(::GetTagsArguments) {
				name = "get-tags"
				description = "Gets all tags in the given forum channel"

				action {
					val forumChannel = arguments.forumChannel.asChannelOf<ForumChannel>()
					val sb = StringBuilder()
					for ((i, tag) in forumChannel.availableTags.withIndex()) {
						if (i > 0) sb.append("\n")
						val emoji = when {
							tag.emojiId != null && tag.emojiName != null -> "<:${tag.emojiName}:${tag.emojiId}> "
							tag.emojiId != null ->
								guild!!.emojis.firstOrNull { it.id == tag.emojiId }?.let { "${it.mention} " } ?: ""
							tag.emojiName != null -> "${tag.emojiName} "
							else -> ""
						}
						sb.append("- ").append(emoji).append(tag.name)
					}
					respondSimple("Available tags for ${forumChannel.mention}:\n\n$sb")
				}
			}

			ephemeralSubCommand(::MigrateTagArguments) {
				name = "migrate-tag"
				description = "Creates a new tag to be used in the given forum channel"

				action {
					val forumChannel = arguments.forumChannel.asChannelOf<ForumChannel>()
					val tagFromName = arguments.tagFrom
					val tagToName = arguments.tagTo

					fun findTag(tagName: String): ForumTag? = forumChannel.availableTags.find { it.name == tagName }

					suspend fun tagNotExists(tagName: String) {
						respondSimple("The tag `$tagName` could not be found in ${forumChannel.mention}!")
					}

					val tagFrom = findTag(tagFromName) ?: return@action tagNotExists(tagFromName)
					val tagTo = findTag(tagToName) ?: return@action tagNotExists(tagToName)

					log.info { "Command forum migrate-tag: Will migrate threads in ${forumChannel.toSimpleString()} with tag ${tagFrom.toSimpleString()} to ${tagTo.toSimpleString()}" }
					val responseMessage = respondSimple(
						"Migrating threads in ${forumChannel.mention} from `$tagFromName` to `$tagToName`...",
						DISCORD_YELLOW
					)

					val tagFromId = tagFrom.id
					val tagToId = tagTo.id
					suspend fun migrateThreads(threads: Flow<ThreadChannel>): Set<Job> =
						threads.filter { it.appliedTags.contains(tagFromId) }
							.map { thread ->
								this@ephemeralSlashCommand.kord.launch {
									thread.edit {
										appliedTags = thread.appliedTags.toMutableList().apply {
											remove(tagFromId)
											add(tagToId)
										}
									}
								}
							}
							.toSet()

					var numUpdated = 0
					log.info { "Command forum migrate-tag: Migrating active threads..." }
					migrateThreads(forumChannel.activeThreads).also { numUpdated += it.size }.joinAll()
					log.info { "Command forum migrate-tag: Migrating public archived threads..." }
					migrateThreads(forumChannel.getPublicArchivedThreads()).also { numUpdated += it.size }.joinAll()
					log.info { "Command forum migrate-tag: Finished migrating $numUpdated threads" }

					loggingService.log(
						"Migrated $numUpdated threads in ${forumChannel.mention} from `$tagFromName` to `$tagToName`",
						user.asUser()
					)
					responseMessage.editSimple(
						"Finished migrating $numUpdated threads in ${forumChannel.mention} from `$tagFromName` to `$tagToName`!",
						DISCORD_GREEN
					)
				}
			}
		}
	}

	abstract inner class ForumCommandArguments : Arguments() {
		val forumChannel: Channel by channel {
			name = "forum-channel"
			description = "The forum channel"
			requireChannelType(ChannelType.GuildForum)
		}
	}

	inner class GetTagsArguments : ForumCommandArguments()

	inner class MigrateTagArguments : ForumCommandArguments() {
		private suspend fun AutoCompleteInteraction.tagAutocomplete() {
			val forumChannelId = command.options["forum-channel"]?.let { (it as ChannelOptionValue).value } ?: return
			val guild = (channel as GuildChannelBehavior).guild
			val forumChannel = guild.getChannel(forumChannelId).asChannelOf<ForumChannel>()
			val tags = forumChannel.availableTags.map { it.name }
			suggestStringCollection(tags, FilterStrategy.Contains)
		}

		val tagFrom: String by string {
			name = "tag-from"
			description = "The tag to migrate threads from"
			autoComplete { tagAutocomplete() }
		}
		val tagTo: String by string {
			name = "tag-to"
			description = "The tag to migrate threads to"
			autoComplete { tagAutocomplete() }
		}
	}
}
