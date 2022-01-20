package me.brightspark.mdcbot.database.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity(name = "bot_property")
@Table(name = "bot_properties")
data class BotProperty(
	@Id
	val name: String,
	@Column(nullable = true)
	var value: String?
)
