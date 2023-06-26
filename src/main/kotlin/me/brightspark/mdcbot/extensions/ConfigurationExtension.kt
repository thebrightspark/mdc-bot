package me.brightspark.mdcbot.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommand
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.components.ComponentContainer
import com.kotlindiscord.kord.extensions.components.applyComponents
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralSelectMenu
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.types.edit
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.GuildBehavior
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import me.brightspark.mdcbot.properties.Property
import mu.KotlinLogging
import org.springframework.stereotype.Component

/*
	TODO: Change these to be a single command that uses button components to select which config to change.
	 On the main config menu it can also state the current values?
 */
@Component
class ConfigurationExtension : BaseExtension("configuration") {
	private val log = KotlinLogging.logger {}

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "config"
			description = "Configuration commands"
			mdcGuild()
			userRequiresAdminPermission()

			ephemeralSubCommand {
				name = "list"
				description = "Lists all current config values"

				action {
					respond {
						embed {
							Property.ALL.forEach { prop ->
								field(prop.name, true) {
									val rawValue = propertyService.getRaw(prop)
									val value = rawValue?.let { prop.deserialiseToDisplayString(guild!!, it) }
										?: Property.VALUE_NONE
									"""
									**Value:** $value
									**Raw:** $rawValue
									""".trimIndent()
								}
							}
						}
					}
				}
			}

			ephemeralSubCommand {
				val property = Property.CHANNEL_LOGS
				setupConfigMenuSingle(
					property = property,
					optionsProvider = { channelChoices(it) },
					readableProvider = { guild, selected -> guild.getChannelOrNull(Snowflake(selected))?.mention },
					actionConsumer = { propertyService.set(property, Snowflake(it)) }
				)
			}

			ephemeralSubCommand {
				val property = Property.ROLE_ADMIN
				setupConfigMenuSingle(
					property = property,
					optionsProvider = { roleChoices(it) },
					readableProvider = { guild, selected -> guild.getRoleOrNull(Snowflake(selected))?.mention },
					actionConsumer = { propertyService.set(property, Snowflake(it)) }
				)
			}

			ephemeralSubCommand {
				val property = Property.CATEGORY_DEV
				setupConfigMenuSingle(
					property = property,
					optionsProvider = { categoryChoices(it) },
					readableProvider = { guild, selected -> guild.getChannelOrNull(Snowflake(selected))?.mention },
					actionConsumer = { propertyService.set(property, Snowflake(it)) }
				)
			}

			ephemeralSubCommand {
				val property = Property.AUTO_DELETE_LEAVERS_CHANNELS
				setupConfigMenuMulti(
					property = property,
					optionsProvider = { channelChoices(it) },
					readableProvider = { guild, selected ->
						selected.mapNotNull { guild.getChannelOrNull(Snowflake(it))?.mention }.joinToString()
					},
					action = { value -> propertyService.set(property, value.map { Snowflake(it) }) }
				)
			}
		}
	}

	private fun <ARGS : Arguments> EphemeralSlashCommand<ARGS, ModalForm>.setupConfigMenuSingle(
		property: Property<*>,
		optionsProvider: (suspend (GuildBehavior) -> List<Pair<String, String>>),
		readableProvider: (suspend (GuildBehavior, String) -> String?),
		actionConsumer: (String) -> Unit
	) {
		name = property.name
		description = "Sets ${property.description}"

		action {
			respond {
				content = "Please select an option:"

				val commandUser = user.asUser()
				lateinit var messageComponents: ComponentContainer
				messageComponents = components {
					ephemeralSelectMenu {
						maximumChoices = 1

						// Options
						optionsProvider(guild!!).forEach { option(it.first, it.second) }

						// Interaction
						action {
							val selectedValue = selected.first()
							actionConsumer(selectedValue)

							val selectedReadable = readableProvider(guild!!, selectedValue)
							loggingService.log(
								"${property.nameReadable} has been set to $selectedReadable ($selectedValue)",
								commandUser
							)
							log.info { "Config ${property.name}: Set to $selectedValue" }

							messageComponents.removeAll()
							edit {
								content = null
								applyComponents(messageComponents)
								embed {
									description = "${property.nameReadable} has been set to $selectedReadable"
								}
							}
						}
					}
				}
			}
		}
	}

	private fun <ARGS : Arguments, PROP> EphemeralSlashCommand<ARGS, ModalForm>.setupConfigMenuMulti(
		property: Property<PROP>,
		optionsProvider: (suspend (GuildBehavior) -> List<Pair<String, String>>),
		readableProvider: (suspend (GuildBehavior, List<String>) -> String?),
		action: (List<String>) -> Unit
	) {
		name = property.name
		description = "Sets ${property.description}"

		action {
			respond {
				content = "Please select any number of options:"

				val commandUser = user.asUser()
				lateinit var messageComponents: ComponentContainer
				messageComponents = components {
					ephemeralSelectMenu {
						maximumChoices = null

						// Options
						optionsProvider(guild!!).forEach { option(it.first, it.second) }

						// Interaction
						action {
							action(selected)

							val selectedReadable = readableProvider(guild!!, selected)
							loggingService.log(
								"${property.nameReadable} has been set to $selectedReadable ($selected)",
								commandUser
							)
							log.info { "Config ${property.name}: Set to $selected" }

							messageComponents.removeAll()
							edit {
								content = null
								applyComponents(messageComponents)
								embed {
									description = "${property.nameReadable} has been set to $selectedReadable"
								}
							}
						}
					}
				}
			}
		}
	}

	private suspend fun channelChoices(guild: GuildBehavior): List<Pair<String, String>> =
		guild.channels.filter { it.type == ChannelType.GuildText }.toList()
			.sortedBy { it.rawPosition }
			.map { it.name to it.id.toString() }

	private suspend fun categoryChoices(guild: GuildBehavior): List<Pair<String, String>> =
		guild.channels.filter { it.type == ChannelType.GuildCategory }.toList()
			.sortedBy { it.rawPosition }
			.map { it.name to it.id.toString() }

	private suspend fun roleChoices(guild: GuildBehavior): List<Pair<String, String>> =
		guild.roles.toList()
			.sortedBy { it.rawPosition }
			.map { it.name to it.id.toString() }
}
