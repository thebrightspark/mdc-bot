package me.brightspark.mdcbot.database.repository

import me.brightspark.mdcbot.database.model.BotProperty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BotPropertyRepo : JpaRepository<BotProperty, String>
