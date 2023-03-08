package me.brightspark.mdcbot.properties

import dev.kord.common.entity.Snowflake

open class PropertyType<T> private constructor(
	val name: String,
	val deserialiser: (String?) -> T,
	val serialiser: (T) -> String?
) {
	companion object {
		private const val NONE_STRING = "<NONE>"
		private val LIST_DELIMITERS = Regex("[, ]+")

		val BOOLEAN: PropertyType<Boolean> = PropertyType(
			name = "Boolean",
			deserialiser = { it == "t" },
			serialiser = { if (it) "t" else "f" }
		)
		val STRING: PropertyType<String> = PropertyType(
			name = "String",
			deserialiser = { it ?: NONE_STRING },
			serialiser = { it }
		)
		val CHANNEL: PropertyType<Snowflake?> = SnowflakePropertyType("Channel")
		val CHANNELS: PropertyType<List<Snowflake>> = ListPropertyType("Channels", CHANNEL)
		val CATEGORY: PropertyType<Snowflake?> = SnowflakePropertyType("Category")
		val ROLE: PropertyType<Snowflake?> = SnowflakePropertyType("Role")
	}

	private class SnowflakePropertyType(name: String) : PropertyType<Snowflake?>(
		name = name,
		deserialiser = { v -> v?.let { Snowflake(it) } },
		serialiser = { it?.toString() }
	)

	private class ListPropertyType<T : Any>(name: String, singularPropType: PropertyType<T?>) : PropertyType<List<T>>(
		name = name,
		deserialiser = { v ->
			v?.run { split(LIST_DELIMITERS).mapNotNull { singularPropType.deserialiser(it) } } ?: emptyList()
		},
		serialiser = { v -> v.joinToString(",") { it.toString() } }
	)

	override fun toString(): String = name
}
