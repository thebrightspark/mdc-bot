package me.brightspark.mdcbot.database.service

import me.brightspark.mdcbot.database.model.BotProperty
import me.brightspark.mdcbot.database.repository.BotPropertyRepo
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
	fun get(propertyId: String): String? = botPropertyRepo.findById(propertyId)
		.orElseGet { botPropertyRepo.save(BotProperty(propertyId, null)) }
		.value

	@CacheEvict(key = "#propertyName")
	fun put(propertyId: String, propertyValue: String?): Unit = botPropertyRepo.findById(propertyId).ifPresentOrElse(
		{ botPropertyRepo.save(it.apply { value = propertyValue }) },
		{ botPropertyRepo.save(BotProperty(propertyId, propertyValue)) }
	)
}
