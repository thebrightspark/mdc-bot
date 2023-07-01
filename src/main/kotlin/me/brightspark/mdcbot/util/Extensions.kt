package me.brightspark.mdcbot.util

import com.kotlindiscord.kord.extensions.DISCORD_BLACK
import com.kotlindiscord.kord.extensions.types.EphemeralInteractionContext
import com.kotlindiscord.kord.extensions.types.PublicInteractionContext
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.Color
import dev.kord.common.entity.ForumTag
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.interaction.followup.EphemeralFollowupMessage
import dev.kord.core.entity.interaction.followup.PublicFollowupMessage
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import java.util.*

fun <T> Optional<T>.ifPresentOrElse(presentConsumer: (T) -> Unit, elseRunnable: () -> Unit): Unit =
	if (isPresent) presentConsumer(get()) else elseRunnable()

fun User.toSimpleString(): String = "${this.username}#${this.discriminator} (${this.id})"

fun Channel.toSimpleString(): String = "${this.data.name.value} (${this.id})"

fun Role.toSimpleString(): String = "${this.name} (${this.id})"

fun ForumTag.toSimpleString(): String = "${this.name} (${this.id})"

suspend fun Kord.getCategory(id: Snowflake): Category? =
	this.getChannel(id)?.takeIf { it is Category }?.let { it as Category }

suspend fun EphemeralInteractionContext.respondSimple(
	message: String,
	color: Color = DISCORD_BLACK
): EphemeralFollowupMessage = respond {
	embed {
		this.color = color
		description = message
	}
}

suspend fun PublicInteractionContext.respondSimple(
	message: String,
	color: Color = DISCORD_BLACK
): PublicFollowupMessage = respond {
	embed {
		this.color = color
		description = message
	}
}

suspend fun EphemeralFollowupMessage.editSimple(
	message: String,
	color: Color = DISCORD_BLACK
): EphemeralFollowupMessage = edit {
	embed {
		this.color = color
		description = message
	}
}
