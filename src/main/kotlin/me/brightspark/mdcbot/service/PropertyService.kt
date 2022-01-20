package me.brightspark.mdcbot.service

import me.brightspark.mdcbot.database.service.BotPropertyService
import me.brightspark.mdcbot.model.PropertyName
import org.springframework.stereotype.Service

@Service
class PropertyService(
	private val botPropertyService: BotPropertyService
) {
	fun get(propertyName: PropertyName): String? = botPropertyService.get(propertyName.name)

	fun set(propertyName: PropertyName, propertyValue: String?) =
		botPropertyService.put(propertyName.name, propertyValue)
}
