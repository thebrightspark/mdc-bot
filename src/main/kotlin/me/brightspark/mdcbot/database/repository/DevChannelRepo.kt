package me.brightspark.mdcbot.database.repository

import me.brightspark.mdcbot.database.model.DevChannel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DevChannelRepo : JpaRepository<DevChannel, Long> {
	fun findByUserId(userId: Long): DevChannel?
}
