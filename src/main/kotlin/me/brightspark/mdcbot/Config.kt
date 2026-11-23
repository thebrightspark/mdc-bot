package me.brightspark.mdcbot

import brightspark.sparkconfig.SparkConfig
import brightspark.sparkconfig.annotation.Category
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

	@Category
	object JobForumTagIds {
		@Config
		var forge: Long = 0

		@Config
		var neoforge: Long = 0

		@Config
		var fabric: Long = 0

		@Config
		var quilt: Long = 0

		@Config
		var plugin: Long = 0

		@Config
		var textures: Long = 0

		@Config
		var models: Long = 0

		@Config
		var sounds: Long = 0

		@Config
		var config: Long = 0

		@Config
		var coding: Long = 0

		@Config
		var revshare: Long = 0

		@Config
		var money: Long = 0

		@Config
		var unpaid: Long = 0
	}

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
