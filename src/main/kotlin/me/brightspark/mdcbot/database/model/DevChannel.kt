package me.brightspark.mdcbot.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity(name = "dev_channel")
@Table(name = "dev_channels")
data class DevChannel(
	@Id
	val channelId: Long,
	@Column(nullable = false)
	var userId: Long,
	@Column(nullable = true)
	var botMessageId: Long,
	@Column(nullable = true)
	var inviteCode: String? = null
)
