package me.brightspark.mdcbot.database

import io.github.oshai.kotlinlogging.KotlinLogging
import me.brightspark.mdcbot.database.table.BotPropertyTable
import me.brightspark.mdcbot.database.table.DevChannelTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.sqlite.SQLiteDataSource

object DbConnection {
	private val log = KotlinLogging.logger {}
	private val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:mdcbot.db" }
	private var connected = false

	fun connect() {
		if (connected) return

		log.info { "Setting up connection to database" }
		Database.connect(dataSource)
		connected = true

		transaction { SchemaUtils.create(BotPropertyTable, DevChannelTable) }
	}
}
