package me.brightspark.mdcbot.extensions

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.threads.edit
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.getChannelOf
import dev.kord.core.behavior.interaction.response.EphemeralMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.Attachment
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.ForumChannel
import dev.kord.core.entity.channel.thread.TextChannelThread
import dev.kord.core.entity.interaction.ModalSubmitInteraction
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.rest.builder.channel.thread.applyTag
import dev.kord.rest.builder.component.actionRow
import dev.kord.rest.builder.message.MessageBuilder
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.commands.application.slash.converters.ChoiceEnum
import dev.kordex.core.components.ComponentContainer
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralStringSelectMenu
import dev.kordex.core.components.forms.ModalForm
import dev.kordex.core.components.menus.string.EphemeralStringSelectMenuContext
import dev.kordex.core.components.menus.string.StringSelectMenu
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.getLocale
import dev.kordex.i18n.Key
import dev.kordex.modules.dev.unsafe.annotations.UnsafeAPI
import dev.kordex.modules.dev.unsafe.components.buttons.InitialInteractionButtonResponse
import dev.kordex.modules.dev.unsafe.extensions.unsafeButton
import i18n.Translations
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import me.brightspark.mdcbot.Config
import me.brightspark.mdcbot.properties.Property
import me.brightspark.mdcbot.service.PropertyService
import me.brightspark.mdcbot.util.simpleEmbed
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.enums.enumEntries

class ForumFormsExtension : BaseExtension("forum-forms") {
	companion object {
		private val REGEX_USER_MENTION = Regex("<@(\\d+)>")
	}

	private val buttonLockId = "$name-button-lock"

	private val cachedJobData: MutableMap<Snowflake, JobData> = mutableMapOf()

	private fun setCachedJobData(messageId: Snowflake, dataSetter: (JobData) -> Unit): Unit =
		dataSetter(cachedJobData.computeIfAbsent(messageId) { JobData() })

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = Translations.ForumForms.Command.CreateJobPost.name
			description = Translations.ForumForms.Command.CreateJobPost.desc

			action {
				val jobData = JobData()
				val response = respond { components { createJobMessageComponents(jobData) } }
				cachedJobData[response.message.id] = jobData.apply {
					originalMessageId = response.id
					interactionToken = response.token
				}
			}
		}

		// Implemented this through the event so that it will always work, even after bot restarts
		event<ButtonInteractionCreateEvent> {
			checkIf { event.interaction.componentId == buttonLockId }
			checkIf { isThreadAuthorOrModerator() }
			action {
				val deferredUpdate = event.interaction.deferPublicMessageUpdate()
				val channel = event.interaction.channel.asChannelOfOrNull<TextChannelThread>() ?: return@action
				val isLocked = channel.isLocked
				channel.edit { locked = !isLocked }
				deferredUpdate.edit { createThreadManagementMessage(!isLocked) }
			}
		}
	}

	@OptIn(UnsafeAPI::class)
	private suspend fun ComponentContainer.createJobMessageComponents(jobData: JobData) {
		enumSelectMenu(
			Translations.ForumForms.Command.CreateJobPost.Components.ModLoader.label,
			jobData.modLoader
		) { it.modLoader = event.interaction.values.mapToEnum() }
		enumSelectMenu(
			Translations.ForumForms.Command.CreateJobPost.Components.WorkType.label,
			jobData.workType
		) { it.workType = event.interaction.values.mapToEnum() }
		enumSelectMenu(
			Translations.ForumForms.Command.CreateJobPost.Components.PayType.label,
			jobData.payType
		) { it.payType = event.interaction.values.mapToEnum() }
		unsafeButton {
			label = Translations.ForumForms.Command.CreateJobPost.Components.NextButton.label
			if (jobData.isFirstPartDataAllSet()) enable() else disable()
			initialResponse = InitialInteractionButtonResponse.None
			action {
				val locale = event.getLocale()
				CreateJobThreadModal().let { modal ->
					modal.sendAndAwait(locale, event.interaction) { modalInteraction ->
						modalInteraction?.let {
							val deferredUpdate = it.deferEphemeralMessageUpdate()
							handleModalSubmit(deferredUpdate, modal, it, jobData)
						} ?: cachedJobData.remove(jobData.originalMessageId!!)
					}
				}
			}
		}
	}

	private suspend inline fun <reified T> ComponentContainer.enumSelectMenu(
		label: Key,
		selected: List<T>,
		noinline jobDataSetter: EphemeralStringSelectMenuContext<*>.(JobData) -> Unit
	) where T : Enum<T>, T : ChoiceEnum {
		ephemeralStringSelectMenu {
			placeholder = label
			maximumChoices = enumValues<T>().size
			addEnumOptions(selected)
			action {
				setCachedJobData(message.id) { jobDataSetter(this, it) }
				handleSelectMenuAction(this)
			}
		}
	}

	suspend fun handleSelectMenuAction(context: EphemeralStringSelectMenuContext<ModalForm>) {
		val isButtonDisabled = context.message.buttons.first().disabled
		val jobData = cachedJobData[context.message.id] ?: return
		if (jobData.isFirstPartDataAllSet() == isButtonDisabled) {
			context.edit {
				components = null
				components { createJobMessageComponents(jobData) }
			}
		}
	}

	private suspend fun handleModalSubmit(
		deferredUpdate: EphemeralMessageInteractionResponseBehavior,
		modal: CreateJobThreadModal,
		modalInteraction: ModalSubmitInteraction,
		jobData: JobData
	) {
		val guild = modalInteraction.kord.getGuild(mdcGuildId)
		val forumChannel =
			guild.getChannelOf<ForumChannel>(PropertyService.get(Property.CHANNEL_JOBS_FORUM)!!)
		val images = modalInteraction.resolvedObjects?.attachments?.values?.filter(Attachment::isImage)
			?.map { it.filename to modalInteraction.kord.resources.httpClient.get(it.url).bodyAsChannel() }

		val thread = createThread(forumChannel, modalInteraction.user, modal, jobData, images)

		deferredUpdate.edit {
			components = mutableListOf()
			content = "Job post created: ${thread.mention}"
		}
		cachedJobData.remove(jobData.originalMessageId!!)
	}

	private suspend fun createThread(
		channel: ForumChannel,
		user: User,
		modal: CreateJobThreadModal,
		jobData: JobData,
		images: List<Pair<String, ByteReadChannel>>?
	): TextChannelThread =
		channel.startPublicThread(modal.name.value!!) {
			message {
				content = """
					**Author:** ${user.mention} (`${user.username}`)
					**Name:** ${modal.name.value}
					**Work Required:** ${jobData.workType.joinReadableNames()}
					**MC Version:** ${modal.mcVersion.value}
					**Mod Loader:** ${jobData.modLoader.joinReadableNames()}
					**Pay Type:** ${jobData.payType.joinReadableNames()}
					**AI Usage:** ${modal.aiUsage.value}
					
					**Details**:
					${modal.details.value}
				""".trimIndent()
				images?.forEach { addFile(it.first, ChannelProvider { it.second }) }
			}
			jobData.workType.forEach { applyTag(it.tagId) }
			jobData.modLoader.forEach { applyTag(it.tagId) }
			jobData.payType.forEach { applyTag(it.tagId) }
			reason = "New job post by ${user.username}"
		}.apply {
			createMessage { createThreadManagementMessage(false) }
		}

	private fun MessageBuilder.createThreadManagementMessage(isThreadLocked: Boolean) {
		simpleEmbed("Author thread management:")
		actionRow {
			if (isThreadLocked)
				interactionButton(ButtonStyle.Primary, buttonLockId) {
					label = Translations.ForumForms.Command.CreateJobPost.ManagementMessage.unlockButton.translate()
				}
			else
				interactionButton(ButtonStyle.Danger, buttonLockId) {
					label = Translations.ForumForms.Command.CreateJobPost.ManagementMessage.lockButton.translate()
				}
		}
	}

	private suspend fun CheckContext<ButtonInteractionCreateEvent>.isThreadAuthorOrModerator(): Boolean {
		val channel = event.interaction.channel.asChannelOfOrNull<TextChannelThread>() ?: return false
		val user = event.interaction.getMember()
		if (user.isModerator()) return true
		val firstMessage = channel.getMessage(channel.id)
		val author = REGEX_USER_MENTION.find(firstMessage.content)?.groupValues[1]?.let { Snowflake(it) }
		return user.id == author
	}

	private inline fun <reified T : Enum<T>> List<String>.mapToEnum(): List<T> = this.map { enumValueOf(it) }

	private fun List<ChoiceEnum>.joinReadableNames(): String = this.joinToString { it.readableName.translate() }

	class CreateJobThreadModal : ModalForm() {
		override var title: Key = Translations.ForumForms.Command.CreateJobPost.Modal.title

		val name = lineText {
			label = Translations.ForumForms.Command.CreateJobPost.Components.Name.label
			placeholder = Translations.ForumForms.Command.CreateJobPost.Components.Name.placeholder
		}
		val mcVersion = lineText {
			label = Translations.ForumForms.Command.CreateJobPost.Components.McVersion.label
			placeholder = Translations.ForumForms.Command.CreateJobPost.Components.McVersion.placeholder
		}
		val details = paragraphText {
			label = Translations.ForumForms.Command.CreateJobPost.Components.Details.label
			placeholder = Translations.ForumForms.Command.CreateJobPost.Components.Details.placeholder
		}
		val aiUsage = lineText {
			label = Translations.ForumForms.Command.CreateJobPost.Components.AiUsage.label
			placeholder = Translations.ForumForms.Command.CreateJobPost.Components.AiUsage.placeholder
		}
		val images = fileUpload {
			label = Translations.ForumForms.Command.CreateJobPost.Components.Images.label
			description = Translations.ForumForms.Command.CreateJobPost.Components.Images.desc
			required = false
			minValues = 0
		}
	}

	enum class WorkType(override val readableName: Key, tagId: Long) : ChoiceEnum, TagId {
		CODING(
			Translations.ForumForms.Command.CreateJobPost.Components.WorkType.Options.coding,
			Config.JobForumTagIds.coding
		),
		TEXTURES(
			Translations.ForumForms.Command.CreateJobPost.Components.WorkType.Options.textures,
			Config.JobForumTagIds.textures
		),
		MODELS(
			Translations.ForumForms.Command.CreateJobPost.Components.WorkType.Options.models,
			Config.JobForumTagIds.models
		),
		SOUNDS(
			Translations.ForumForms.Command.CreateJobPost.Components.WorkType.Options.sounds,
			Config.JobForumTagIds.sounds
		),
		CONFIG(
			Translations.ForumForms.Command.CreateJobPost.Components.WorkType.Options.config,
			Config.JobForumTagIds.config
		);

		override val tagId: Snowflake = Snowflake(tagId)
	}

	enum class ModLoader(override val readableName: Key, tagId: Long) : ChoiceEnum, TagId {
		FORGE(
			Translations.ForumForms.Command.CreateJobPost.Components.ModLoader.Options.forge,
			Config.JobForumTagIds.forge
		),
		NEOFORGE(
			Translations.ForumForms.Command.CreateJobPost.Components.ModLoader.Options.neoforge,
			Config.JobForumTagIds.neoforge
		),
		FABRIC(
			Translations.ForumForms.Command.CreateJobPost.Components.ModLoader.Options.fabric,
			Config.JobForumTagIds.fabric
		),
		QUILT(
			Translations.ForumForms.Command.CreateJobPost.Components.ModLoader.Options.quilt,
			Config.JobForumTagIds.quilt
		),
		PLUGIN(
			Translations.ForumForms.Command.CreateJobPost.Components.ModLoader.Options.plugin,
			Config.JobForumTagIds.plugin
		);

		override val tagId: Snowflake = Snowflake(tagId)
	}

	enum class PayType(override val readableName: Key, tagId: Long) : ChoiceEnum, TagId {
		UNPAID(
			Translations.ForumForms.Command.CreateJobPost.Components.PayType.Options.unpaid,
			Config.JobForumTagIds.unpaid
		),
		REVENUE_SHARE(
			Translations.ForumForms.Command.CreateJobPost.Components.PayType.Options.revShare,
			Config.JobForumTagIds.revshare
		),
		MONEY(
			Translations.ForumForms.Command.CreateJobPost.Components.PayType.Options.money,
			Config.JobForumTagIds.money
		);

		override val tagId: Snowflake = Snowflake(tagId)
	}

	private interface TagId {
		val tagId: Snowflake
	}

	private suspend inline fun <reified T> StringSelectMenu.addEnumOptions(defaultValues: List<T> = emptyList()) where T : Enum<T>, T : ChoiceEnum =
		enumEntries<T>().forEach { option(it.readableName, it.name) { default = it in defaultValues } }

	class JobData {
		var originalMessageId: Snowflake? = null
		var interactionToken: String? = null
		var modLoader: List<ModLoader> = emptyList()
		var workType: List<WorkType> = emptyList()
		var payType: List<PayType> = emptyList()

		fun isFirstPartDataAllSet(): Boolean = modLoader.isNotEmpty() && workType.isNotEmpty() && payType.isNotEmpty()
	}
}
