package me.brightspark.mdcbot.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity(name = "bot_property")
@Table(name = "bot_properties")
data class BotProperty(
	@Id
	val name: String,
	@Column(nullable = true)
	var value: String?
)
