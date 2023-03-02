package me.brightspark.mdcbot.properties

import me.brightspark.mdcbot.properties.PropertyType.Companion.SNOWFLAKE
import me.brightspark.mdcbot.properties.PropertyType.Companion.SNOWFLAKES

class Property<T>(
	val propType: PropertyType<T>,
	val name: String,
	val nameReadable: String,
	val description: String
) {
	companion object {
		private val ALL = mutableSetOf<Property<*>>()

		val CHANNEL_LOGS = create("log", "Log channel", "the log channel for the bot", SNOWFLAKE)
		val CATEGORY_DEV =
			create(
				"category",
				"Dev category",
				"the developer category, where all developer channels will be created under",
				SNOWFLAKE
			)
		val ROLE_ADMIN = create("role", "Bot admin role", "bot admin role", SNOWFLAKE)
		val AUTO_DELETE_LEAVERS_CHANNELS =
			create("auto-delete-leavers-messages-channels", "Auto delete leavers channels", "", SNOWFLAKES)

		private fun <T> create(
			name: String,
			nameReadable: String,
			description: String,
			type: PropertyType<T>
		): Property<T> = Property(type, name, nameReadable, description).apply { ALL += this }
	}

	val defaultValue: T
		get() = propType.defaultValue

	fun deserialise(value: String): T = propType.deserialiser(value)

	fun serialise(value: T): String = propType.serialiser(value)
}
