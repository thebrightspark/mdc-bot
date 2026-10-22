package me.brightspark.mdcbot.extensions

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.ChannelBehavior
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.entity.Member
import dev.kord.core.entity.channel.CategorizableChannel
import dev.kord.core.entity.interaction.Interaction
import dev.kord.core.event.Event
import dev.kord.gateway.Intent
import dev.kordex.core.checks.guildFor
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.checks.types.CheckContextWithCache
import dev.kordex.core.commands.application.ApplicationCommand
import dev.kordex.core.events.EventHandler
import dev.kordex.core.extensions.Extension
import dev.kordex.core.utils.hasPermissions
import dev.kordex.i18n.Key
import i18n.Translations
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.service.LoggingService
import me.brightspark.mdcbot.service.PropertyService
import org.springframework.beans.factory.annotation.Autowired

abstract class BaseExtension(override val name: String) : Extension() {
	@Autowired
	protected lateinit var mdcGuildId: Snowflake

	@Autowired
	protected lateinit var loggingService: LoggingService

	@Autowired
	protected lateinit var propertyService: PropertyService

	/**
	 * Sets all the given intents to this extension
	 */
	protected fun intents(vararg intents: Intent) {
		this.intents.addAll(intents)
	}

	/**
	 * Locks the [ApplicationCommand] to the MDC guild
	 */
	protected fun ApplicationCommand<*>.mdcGuild(): Unit = guild(mdcGuildId)

	/**
	 * Sets the default required permission for this command to be [Permission.Administrator]
	 */
	protected fun ApplicationCommand<*>.userRequiresAdminPermission(): Unit =
		requirePermission(Permission.Administrator)

	/**
	 * Sets the default required permission for this command to be [Permission.ManageMessages]
	 *
	 * We're assuming that [Permission.ManageMessages] is a suitable permission that moderators would have, but that can
	 * be changed by server admins
	 */
	protected fun ApplicationCommand<*>.userRequiresModeratorPermission(): Unit =
		requirePermission(Permission.ManageMessages)

	protected suspend fun Interaction.getMember(): Member = user.asMember(data.guildId.value!!)

	protected fun Member.isModerator(): Boolean =
		propertyService.get(Property.ROLE_MODERATOR).let { this.roleIds.contains(it) }

	protected fun <T : Event> EventHandler<T>.checkIf(
		message: Key? = null,
		callback: suspend CheckContextWithCache<T>.() -> Boolean
	) {
		this.check { failIfNot(message) { callback() } }
	}

	/**
	 * Fail if the bot does not have the permissions
	 */
	protected suspend fun CheckContext<*>.botHasPermissions(vararg permissions: Permission) {
		failIfNot(Translations.Error.Bot.permission.withOrdinalPlaceholders(permissions.size, permissions.joinToString())) {
			guildFor(event)?.getMemberOrNull(kord.selfId)?.hasPermissions(*permissions) ?: false
		}
	}

	protected suspend fun propContainsChannelOrParentCategory(
		property: Property<List<Snowflake>>,
		channel: ChannelBehavior
	): Boolean {
		val channelId = channel.id
		val propChannels = propertyService.get(property)
		fun propHasId(id: Snowflake): Boolean = propChannels?.any { id == it } == true

		return propHasId(channelId) ||
			channel.asChannelOf<CategorizableChannel>().categoryId?.let { propHasId(it) } ?: false
	}
}
