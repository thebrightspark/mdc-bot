package me.brightspark.mdcbot.properties

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import me.brightspark.mdcbot.properties.PropertyType.Companion.BOOLEAN
import me.brightspark.mdcbot.properties.PropertyType.Companion.CATEGORY
import me.brightspark.mdcbot.properties.PropertyType.Companion.CHANNEL
import me.brightspark.mdcbot.properties.PropertyType.Companion.CHANNELS
import me.brightspark.mdcbot.properties.PropertyType.Companion.ROLE
import me.brightspark.mdcbot.properties.PropertyType.Companion.STRING

class Property<T : Any?>(
	val propType: PropertyType<T>,
	val name: String,
	val nameReadable: String,
	val description: String
) {
	companion object {
		private val ALL_SET = mutableSetOf<Property<*>>()
		const val VALUE_NONE = "<NONE>"
		val ALL: List<Property<out Any?>> by lazy { ALL_SET.sortedBy { it.name } }

		val CHANNEL_LOGS = create(
			name = "log",
			nameReadable = "Log channel",
			description = "the log channel for the bot",
			type = CHANNEL
		)
		val CATEGORY_DEV = create(
			name = "category",
			nameReadable = "Dev category",
			description = "the developer category, where all developer channels will be created under",
			type = CATEGORY
		)
		val ROLE_MODERATOR = create(
			name = "moderator-role",
			nameReadable = "Server moderator role",
			description = "the server moderator role",
			type = ROLE
		)
		val AUTO_PUBLISH_CHANNELS = create(
			name = "auto-publish-channels",
			nameReadable = "Auto publish channels",
			description = "the channels to automatically publish all messages that are posted in",
			type = CHANNELS
		)
		val MODERATE_INVITES_CHANNEL_IGNORE = create(
			name = "moderate-invites-channel-ignore",
			nameReadable = "Auto-Moderate Invites Ignore Channels",
			description = "the channels to ignore when auto-moderating posted Discord server invite links",
			type = CHANNELS
		)

		private fun <T> create(
			name: String,
			nameReadable: String,
			description: String,
			type: PropertyType<T>
		): Property<T> = Property(type, name, nameReadable, description).apply { ALL_SET += this }
	}

	fun deserialise(value: String): T = propType.deserialiser(value)

	fun serialise(value: T): String? = propType.serialiser(value)

	suspend fun deserialiseToDisplayString(guild: GuildBehavior, value: String): String {
		val deserialised = deserialise(value)
		return when (propType) {
			BOOLEAN -> if (deserialised as Boolean) "True" else "False"
			STRING -> deserialised as String
			CHANNEL -> (deserialised as Snowflake?)?.let { guild.getChannelOrNull(it)?.mention } ?: VALUE_NONE
			CHANNELS -> {
				@Suppress("UNCHECKED_CAST")
				val list: List<Snowflake> = deserialised as List<Snowflake>
				list.mapNotNull { guild.getChannelOrNull(it)?.mention }.joinToString()
			}
			CATEGORY -> (deserialised as Snowflake?)?.let { guild.getChannelOrNull(it)?.name } ?: VALUE_NONE
			ROLE -> (deserialised as Snowflake?)?.let { guild.getRoleOrNull(it)?.mention } ?: VALUE_NONE
			else -> error("Unhandled property type $propType")
		}
	}
}
