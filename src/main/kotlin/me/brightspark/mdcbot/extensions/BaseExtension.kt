package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommand
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.interaction.Interaction
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.service.PropertyService
import org.springframework.beans.factory.annotation.Autowired

abstract class BaseExtension : Extension() {
	@Autowired
	protected lateinit var mdcGuildId: Snowflake

	@Autowired
	protected lateinit var loggingService: LoggingService

	@Autowired
	protected lateinit var propertyService: PropertyService

	protected fun ApplicationCommand<*>.mdcGuild(): Unit = guild(mdcGuildId)

	protected suspend fun Interaction.getMember(): Member = user.asMember(data.guildId.value!!)

	private suspend fun Member.isAdmin(): Boolean = this.hasPermission(Permission.Administrator)
		|| propertyService.get(Property.ROLE_ADMIN).let { this.roleIds.contains(it) }

	private suspend fun Member.isModerator(): Boolean =
		this.isAdmin() || propertyService.get(Property.ROLE_MODERATOR).let { this.roleIds.contains(it) }

	/**
	 * Fail if the user is not a server admin or doesn't have the bot admin role
	 */
	protected suspend fun CheckContext<ApplicationCommandInteractionCreateEvent>.isAdmin() {
		failIfNot("Only bot admins can use this command!") { event.interaction.getMember().isAdmin() }
	}

	/**
	 * Fail if the user does not have the server's moderator role
	 */
	protected suspend fun CheckContext<ApplicationCommandInteractionCreateEvent>.isModerator() {
		failIfNot("Only moderators can use this command!") { event.interaction.getMember().isModerator() }
	}

	/**
	 * Fail if the bot does not have the permissions
	 */
	protected suspend fun CheckContext<*>.botHasPermissions(vararg permissions: Permission) {
		failIfNot("Missing permission${if (permissions.size != 1) "s" else ""} ${permissions.joinToString()}!") {
			guildFor(event)?.getMemberOrNull(kord.selfId)?.hasPermissions(*permissions) ?: false
		}
	}
}
