package me.brightspark.mdcbot.extensions

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
import dev.kord.gateway.Intent
import dev.kordex.core.DISCORD_GREEN
import dev.kordex.core.DISCORD_YELLOW
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.converters.impl.channel
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.utils.FilterStrategy
import dev.kordex.core.utils.suggestStringCollection
import i18n.Translations
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.util.editSimple
import me.brightspark.mdcbot.util.respondSimple
import me.brightspark.mdcbot.util.toSimpleString

class ForumHelperExtension : BaseExtension("forum-helper") {
	override suspend fun setup() {
		intents(Intent.Guilds)

		ephemeralSlashCommand {
			name = Translations.Forum.Command.name
			description = Translations.Forum.Command.desc
			mdcGuild()
			userRequiresModeratorPermission()

			ephemeralSubCommand(::GetTagsArguments) {
				name = Translations.Forum.Command.GetTags.name
				description = Translations.Forum.Command.GetTags.desc

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
					respondSimple(
						Translations.Forum.Command.GetTags.Response.success
							.withOrdinalPlaceholders(forumChannel.mention, sb)
					)
				}
			}

			ephemeralSubCommand(::MigrateTagArguments) {
				name = Translations.Forum.Command.MigrateTag.name
				description = Translations.Forum.Command.MigrateTag.desc

				action {
					val forumChannel = arguments.forumChannel.asChannelOf<ForumChannel>()
					val tagFromName = arguments.tagFrom
					val tagToName = arguments.tagTo

					fun findTag(tagName: String): ForumTag? = forumChannel.availableTags.find { it.name == tagName }

					suspend fun tagNotExists(tagName: String) {
						respondSimple(
							Translations.Forum.Command.MigrateTag.Response.notExist
								.withOrdinalPlaceholders(tagName, forumChannel.mention)
						)
					}

					val tagFrom = findTag(tagFromName) ?: return@action tagNotExists(tagFromName)
					val tagTo = findTag(tagToName) ?: return@action tagNotExists(tagToName)

					log.info { "Command forum migrate-tag: Will migrate threads in ${forumChannel.toSimpleString()} with tag ${tagFrom.toSimpleString()} to ${tagTo.toSimpleString()}" }
					val responseMessage = respondSimple(
						Translations.Forum.Command.MigrateTag.Response.start
							.withOrdinalPlaceholders(forumChannel.mention, tagFromName, tagToName),
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

					LoggingService.log(
						"Migrated $numUpdated threads in ${forumChannel.mention} from `$tagFromName` to `$tagToName`",
						user.asUser()
					)
					responseMessage.editSimple(
						Translations.Forum.Command.MigrateTag.Response.finished
							.withOrdinalPlaceholders(numUpdated, forumChannel.mention, tagFromName, tagToName),
						DISCORD_GREEN
					)
				}
			}
		}
	}

	abstract class ForumCommandArguments : Arguments() {
		val forumChannel: Channel by channel {
			name = Translations.Forum.Command.Arg.Channel.name
			description = Translations.Forum.Command.Arg.Channel.desc
			requireChannelType(ChannelType.GuildForum)
		}
	}

	class GetTagsArguments : ForumCommandArguments()

	class MigrateTagArguments : ForumCommandArguments() {
		private suspend fun AutoCompleteInteraction.tagAutocomplete() {
			val forumChannelId = command.options["forum-channel"]?.let { (it as ChannelOptionValue).value } ?: return
			val guild = (channel as GuildChannelBehavior).guild
			val forumChannel = guild.getChannel(forumChannelId).asChannelOf<ForumChannel>()
			val tags = forumChannel.availableTags.map { it.name }
			suggestStringCollection(tags, FilterStrategy.Contains)
		}

		val tagFrom: String by string {
			name = Translations.Forum.Command.MigrateTag.Arg.TagFrom.name
			description = Translations.Forum.Command.MigrateTag.Arg.TagFrom.desc
			autoComplete { tagAutocomplete() }
		}
		val tagTo: String by string {
			name = Translations.Forum.Command.MigrateTag.Arg.TagTo.name
			description = Translations.Forum.Command.MigrateTag.Arg.TagTo.desc
			autoComplete { tagAutocomplete() }
		}
	}
}
