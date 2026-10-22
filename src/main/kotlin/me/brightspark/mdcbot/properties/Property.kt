package me.brightspark.mdcbot.properties

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kordex.i18n.Key
import i18n.Translations
import me.brightspark.mdcbot.properties.PropertyType.Companion.BOOLEAN
import me.brightspark.mdcbot.properties.PropertyType.Companion.CATEGORY
import me.brightspark.mdcbot.properties.PropertyType.Companion.CHANNEL
import me.brightspark.mdcbot.properties.PropertyType.Companion.CHANNELS
import me.brightspark.mdcbot.properties.PropertyType.Companion.ROLE
import me.brightspark.mdcbot.properties.PropertyType.Companion.STRING

class Property<T>(
	val propType: PropertyType<T>,
	val id: String,
	val name: Key,
	val longName: Key,
	val description: Key
) {
	val nameTranslated: String by lazy { name.translate() }
	val longNameTranslated: String by lazy { longName.translate() }

	companion object {
		private val ALL_SET = mutableSetOf<Property<*>>()
		const val VALUE_NONE = "<NONE>"
		val ALL: List<Property<out Any?>> by lazy { ALL_SET.sortedBy { it.nameTranslated } }

		val CHANNEL_LOGS = create(
			type = CHANNEL,
			id = "channel.logs",
			name = Translations.Property.ChannelLogs.name,
			longName = Translations.Property.ChannelLogs.longName,
			description = Translations.Property.ChannelLogs.desc
		)
		val CATEGORY_DEV = create(
			type = CATEGORY,
			id = "category.dev",
			name = Translations.Property.CategoryDev.name,
			longName = Translations.Property.CategoryDev.longName,
			description = Translations.Property.CategoryDev.desc
		)
		val ROLE_MODERATOR = create(
			type = ROLE,
			id = "role.moderator",
			name = Translations.Property.RoleModerator.name,
			longName = Translations.Property.RoleModerator.longName,
			description = Translations.Property.RoleModerator.desc
		)
		val AUTO_PUBLISH_CHANNELS = create(
			type = CHANNELS,
			id = "channels.autopublish",
			name = Translations.Property.AutoPublishChannels.name,
			longName = Translations.Property.AutoPublishChannels.longName,
			description = Translations.Property.AutoPublishChannels.desc
		)

		private fun <T> create(
			type: PropertyType<T>,
			id: String,
			name: Key,
			longName: Key,
			description: Key
		): Property<T> = Property(type, id, name, longName, description).apply { ALL_SET += this }
	}

	fun deserialise(value: String): T = propType.deserialiser(value)

	fun serialise(value: T): String? = propType.serialiser(value)

	suspend fun deserialiseToDisplayString(guild: GuildBehavior, value: String?): String {
		if (value == null) return VALUE_NONE
		val deserialised = deserialise(value)
		@Suppress("UNCHECKED_CAST")
		return when (propType) {
			BOOLEAN -> if (deserialised as Boolean) "True" else "False"
			STRING -> (deserialised as String).ifBlank { VALUE_NONE }
			ROLE -> (deserialised as Snowflake?)?.let { guild.getRoleOrNull(it)?.mention } ?: VALUE_NONE
			CATEGORY -> (deserialised as Snowflake?)?.let { guild.getChannelOrNull(it)?.name } ?: VALUE_NONE
			CHANNEL -> (deserialised as Snowflake?)?.let { guild.getChannelOrNull(it)?.mention } ?: VALUE_NONE
			CHANNELS -> (deserialised as List<Snowflake>).let { list ->
				if (list.isEmpty())
					VALUE_NONE
				else
					list.mapNotNull { guild.getChannelOrNull(it)?.mention }.joinToString()
			}

			else -> error("Unhandled property type $propType")
		}
	}
}
