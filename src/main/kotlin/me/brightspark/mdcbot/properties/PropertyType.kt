package me.brightspark.mdcbot.properties

import dev.kord.common.entity.Snowflake

class PropertyType<T>(
	val name: String,
	val defaultValue: T,
	val deserialiser: (String) -> T,
	val serialiser: (T) -> String = { it.toString() },
) {
	companion object {
		private val LIST_DELIMITERS = Regex("[, ]+")

		val BOOLEAN = PropertyType("Boolean", false, { it == "t" }, { if (it) "t" else "f" })
		val STRING = PropertyType("String", "", { it })
		val SNOWFLAKE = PropertyType("Snowflake", null, { Snowflake(it) }, { it.toString() })
		val SNOWFLAKES = PropertyType(
			"Snowflakes",
			emptyList(),
			{ prop -> prop.split(LIST_DELIMITERS).mapNotNull { SNOWFLAKE.deserialiser(it) } },
			{ snowflakes -> snowflakes.joinToString(",") { SNOWFLAKE.serialiser(it) } }
		)
	}
}
