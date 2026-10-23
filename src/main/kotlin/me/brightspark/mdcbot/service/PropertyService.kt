package me.brightspark.mdcbot.service

import me.brightspark.mdcbot.database.service.BotPropertyService
import me.brightspark.mdcbot.properties.Property

object PropertyService {
	fun getRaw(property: Property<*>): String? = BotPropertyService.get(property.id)

	fun <T> get(property: Property<T>): T? = getRaw(property)?.let { property.deserialise(it) }

	fun <T> set(property: Property<T>, propertyValue: T) =
		BotPropertyService.put(property.id, propertyValue?.let { property.serialise(it) })

	/**
	 * Util function to check if a single value of a [List] type property matches the [predicate].
	 */
	fun <T> contains(property: Property<List<T>>, predicate: (T) -> Boolean): Boolean =
		get(property)?.any(predicate) ?: false
}
