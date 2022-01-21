package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.checks.types.CheckContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.hasPermission
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
import dev.kord.core.entity.interaction.Interaction
import dev.kord.core.event.interaction.ApplicationInteractionCreateEvent
import me.brightspark.mdcbot.model.PropertyName
import me.brightspark.mdcbot.service.PropertyService

abstract class BaseExtension(
	private val propertyService: PropertyService
) : Extension() {
	protected suspend fun Interaction.getMember(): Member = user.asMember(data.guildId.value!!)

	/**
	 * Fail if the user is not a server admin or doesn't have the bot admin role
	 */
	protected suspend fun CheckContext<ApplicationInteractionCreateEvent>.isAdmin() {
		failIfNot("Only bot admins can use this command!") {
			val member = event.interaction.getMember()
			return@failIfNot member.hasPermission(Permission.Administrator)
				|| propertyService.get(PropertyName.ROLE_ADMIN)?.let { member.roleIds.contains(Snowflake(it)) } ?: false
		}
	}
}
