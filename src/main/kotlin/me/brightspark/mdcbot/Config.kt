package me.brightspark.mdcbot

import brightspark.sparkconfig.SparkConfig
import brightspark.sparkconfig.annotation.Config
import brightspark.sparkconfig.io.ConfigType
import brightspark.sparkconfig.sparkConfig
import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.launch
import kotlin.io.path.Path

private val FILE_PATH = Path("config.yaml")
private val CONFIG: SparkConfig = sparkConfig(me.brightspark.mdcbot.Config::class, FILE_PATH, ConfigType.YAML)

object Config {
	@Config(comment = "The server ID")
	var guild: String = ""

	var guildId: Snowflake = Snowflake.min

	@Config(comment = "The bot token")
	var token: String = ""

	fun read() {
		CONFIG.read()
		guildId = Snowflake(guild)
	}

	fun write() {
		CONFIG.write()
		guild = guildId.toString()
	}

	fun writeAsync() {
		IO_SCOPE.launch { write() }
	}
}
