package me.brightspark.mdcbot.database.service

import me.brightspark.mdcbot.database.model.DevChannel
import me.brightspark.mdcbot.database.repository.DevChannelRepo
import mu.KotlinLogging
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class DevChannelService(
	private val devChannelRepo: DevChannelRepo
) {
	companion object {
		private const val CACHE_BY_CHANNEL = "devChannels_byChannel"
		private const val CACHE_BY_USER = "devChannels_byUser"
	}

	private val log = KotlinLogging.logger {}

	@Cacheable(CACHE_BY_CHANNEL)
	fun getByChannelId(channelId: Long): DevChannel? = devChannelRepo.findByIdOrNull(channelId)

	@Cacheable(CACHE_BY_USER)
	fun getByUserId(userId: Long): DevChannel? = devChannelRepo.findByUserId(userId)

	@Caching(
		put = [
			CachePut(cacheNames = [CACHE_BY_CHANNEL], key = "#devChannel.channelId"),
			CachePut(cacheNames = [CACHE_BY_USER], key = "#devChannel.userId")
		]
	)
	fun save(devChannel: DevChannel): DevChannel = devChannelRepo.save(devChannel)

	@Caching(
		evict = [
			CacheEvict(cacheNames = [CACHE_BY_CHANNEL], key = "#devChannel.channelId"),
			CacheEvict(cacheNames = [CACHE_BY_USER], key = "#devChannel.userId")
		]
	)
	fun remove(devChannel: DevChannel) {
		devChannelRepo.delete(devChannel)
		log.info { "remove: Removed entry $devChannel" }
	}

	@Caching(
		evict = [
			CacheEvict(cacheNames = [CACHE_BY_CHANNEL], allEntries = true),
			CacheEvict(cacheNames = [CACHE_BY_USER], allEntries = true)
		]
	)
	fun removeAll() {
		val count = devChannelRepo.count()
		devChannelRepo.deleteAll()
		log.info { "removeAll: Removed all $count entries" }
	}
}
