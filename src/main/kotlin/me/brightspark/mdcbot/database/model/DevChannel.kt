package me.brightspark.mdcbot.database.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity(name = "dev_channel")
@Table(name = "dev_channels")
data class DevChannel(
	@Id
	val channelId: Long,
	@Column(nullable = false)
	var userId: Long,
	@Column(nullable = true)
	var botMessageId: Long
)
