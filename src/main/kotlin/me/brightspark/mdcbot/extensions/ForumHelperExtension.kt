package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.channel
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.ForumChannel
import dev.kord.rest.builder.message.create.embed
import kotlinx.coroutines.flow.firstOrNull
import mu.KotlinLogging
import org.springframework.stereotype.Component

/*
Commands:
	add-tag <channel> [emoji] <name>
		Adds a new tag
	remove-tag <channel> <name>
		Removes an existing tag (add warning about posts using this tag)
	migrate-tag <channel> <name> <other_name>
		Replaces the given tag on all posts with the other tag
 */
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
					respond { embed { description = "Available tags for ${forumChannel.mention}:\n\n$sb" } }
				}
			}

//			ephemeralSubCommand {
//				name = "create-tag"
//				description = "Creates a new tag to be used in the given forum channel"
//			}
		}
	}

	inner class GetTagsArguments : Arguments() {
		val forumChannel: Channel by channel {
			name = "forum-channel"
			description = "The forum channel"
			requireChannelType(ChannelType.GuildForum)
		}
	}
}
