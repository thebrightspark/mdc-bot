package me.brightspark.mdcbot

import dev.kordex.core.ExtensibleBot
import dev.kordex.core.checks.inGuild
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.brightspark.mdcbot.database.DbConnection
import me.brightspark.mdcbot.extensions.ConfigurationExtension
import me.brightspark.mdcbot.extensions.DeleteSusFilesExtension
import me.brightspark.mdcbot.extensions.DevChannelExtension
import me.brightspark.mdcbot.extensions.ForumHelperExtension

val COROUTINE_SCOPE = CoroutineScope(Dispatchers.Default)
val IO_SCOPE = CoroutineScope(Dispatchers.IO)

suspend fun main() {
	Config.read()
	DbConnection.connect()

	ExtensibleBot(Config.token) {
		intents(addDefaultIntents = false, addExtensionIntents = true) {}
		applicationCommands {
			slashCommandCheck { inGuild(Config.guildId) }
			defaultScope.limitToGuild(Config.guildId) // Just in-case I miss any
		}
		extensions {
			add(::ConfigurationExtension)
			add(::DeleteSusFilesExtension)
			add(::DevChannelExtension)
			add(::ForumHelperExtension)
		}
	}.start()
}
