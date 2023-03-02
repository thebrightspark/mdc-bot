package me.brightspark.mdcbot.service

import me.brightspark.mdcbot.database.service.BotPropertyService
import me.brightspark.mdcbot.properties.Property
import org.springframework.stereotype.Service

@Service
class PropertyService(
	private val botPropertyService: BotPropertyService
) {
	fun <T> get(property: Property<T>): T =
		botPropertyService.get(property.name)?.let { property.deserialise(it) } ?: property.defaultValue

	fun <T> set(property: Property<T>, propertyValue: T) =
		botPropertyService.put(property.name, propertyValue?.let { property.serialise(it) })
}
