package me.brightspark.mdcbot

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import kotlin.concurrent.thread

@SpringBootApplication
class MdcBotApplication {
	@Bean
	fun coroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default)

	@Bean
	fun mdcGuildId(@Value("\${bot.guild}") guildId: Long): Snowflake = Snowflake(guildId)

	@Bean
	fun bot(
		@Value("\${bot.token}") token: String,
		coroutineScope: CoroutineScope,
		extensions: Collection<Extension>,
		mdcGuildId: Snowflake
	): ExtensibleBot = runBlocking {
		ExtensibleBot(token) {
			intents(addDefaultIntents = false, addExtensionIntents = true) {}
			applicationCommands {
				slashCommandCheck { inGuild(mdcGuildId) }
				defaultGuild(mdcGuildId) // Just in-case I miss any
			}
			extensions.forEach { extensionsBuilder.add { it } }
		}.apply { thread { runBlocking { start() } } }
	}
}

fun main(args: Array<String>) {
	runApplication<MdcBotApplication>(*args)
}
