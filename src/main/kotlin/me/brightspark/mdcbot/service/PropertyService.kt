package me.brightspark.mdcbot.service

import me.brightspark.mdcbot.database.service.BotPropertyService
import me.brightspark.mdcbot.properties.Property
import org.springframework.stereotype.Service

@Service
class PropertyService(
	private val botPropertyService: BotPropertyService
) {
	fun getRaw(property: Property<*>): String? = botPropertyService.get(property.name)

	fun <T : Any?> get(property: Property<T>): T? = getRaw(property)?.let { property.deserialise(it) }

	fun <T : Any?> set(property: Property<T>, propertyValue: T) =
		botPropertyService.put(property.name, propertyValue?.let { property.serialise(it) })

	/**
	 * Util function to check if a single value of a [List] type property matches the [predicate].
	 */
	fun <T : Any?> contains(property: Property<List<T>>, predicate: (T) -> Boolean): Boolean =
		get(property)?.any(predicate) ?: false
}
