package me.brightspark.mdcbot.database.service

import me.brightspark.mdcbot.database.model.BotProperty
import me.brightspark.mdcbot.database.repository.BotPropertyRepo
import me.brightspark.mdcbot.util.ifPresentOrElse
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
@CacheConfig(cacheNames = ["botProperties"])
class BotPropertyService(
	private val botPropertyRepo: BotPropertyRepo
) {
	@Cacheable
	fun get(propertyName: String): String? = botPropertyRepo.findById(propertyName)
		.orElseGet { botPropertyRepo.save(BotProperty(propertyName, null)) }
		.value

	@CacheEvict(key = "#propertyName")
	fun put(propertyName: String, propertyValue: String?): Unit = botPropertyRepo.findById(propertyName).ifPresentOrElse(
		{ botPropertyRepo.save(it.apply { value = propertyValue }) },
		{ botPropertyRepo.save(BotProperty(propertyName, propertyValue)) }
	)
}
