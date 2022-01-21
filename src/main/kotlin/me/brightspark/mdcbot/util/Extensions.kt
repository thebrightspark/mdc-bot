package me.brightspark.mdcbot.util

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Category
import dev.kord.core.entity.channel.Channel

fun User.toSimpleString(): String = "${this.username}#${this.discriminator} (${this.id})"

fun Channel.toSimpleString(): String = "${this.data.name.value} (${this.id})"

fun Role.toSimpleString(): String = "${this.name} (${this.id})"

suspend fun Kord.getCategory(id: Snowflake): Category? =
	this.getChannel(id)?.takeIf { it is Category }?.let { it as Category }
