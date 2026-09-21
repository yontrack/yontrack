package net.nemerosa.ontrack.extension.notifications.mail

import tools.jackson.databind.JsonNode
import jakarta.annotation.PostConstruct
import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchNullableString
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
@APIDescription("Sending a message by mail. The notification template is used for the body of the mail.")
@Documentation(MailNotificationChannelConfig::class)
@Documentation(MailNotificationChannelOutput::class, section = "output")
class MailNotificationChannel(
    javaMailSender: JavaMailSender?,
    private val mailService: MailService,
    private val htmlNotificationEventRenderer: HtmlNotificationEventRenderer,
    private val eventTemplatingService: EventTemplatingService,
    private val environment: Environment,
) : AbstractNotificationChannel<MailNotificationChannelConfig, MailNotificationChannelOutput>(
    MailNotificationChannelConfig::class
) {

    private val logger: Logger = LoggerFactory.getLogger(MailNotificationChannel::class.java)

    override val type: String = "mail"
    override val displayName: String = "Mail"
    override val enabled: Boolean = javaMailSender != null || environment.activeProfiles.contains(RunProfile.DEV)

    @PostConstruct
    fun log() {
        logger.info("Mail notification channel enabled.")
    }

    override fun validateParsedConfig(config: MailNotificationChannelConfig) {
        if (config.to.isBlank()) {
            throw EventSubscriptionConfigException("Recipients (to) cannot be blank")
        }
        if (config.subject.isBlank()) {
            throw EventSubscriptionConfigException("Subject cannot be blank")
        }
    }

    override fun mergeConfig(
        a: MailNotificationChannelConfig,
        changes: JsonNode
    ) = MailNotificationChannelConfig(
        to = patchString(changes, a::to),
        cc = patchNullableString(changes, a::cc),
        subject = patchString(changes, a::subject),
    )

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            MailNotificationChannelConfig::subject.name to text
        ).asJson()

    override fun publish(
        recordId: String,
        config: MailNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: MailNotificationChannelOutput) -> MailNotificationChannelOutput
    ): NotificationResult<MailNotificationChannelOutput> {
        // Subject as a template
        val subject = eventTemplatingService.render(
            template = config.subject,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE, // Using plain text for the subject
        )
        // Formatting the message
        val message = eventTemplatingService.renderEvent(
            event = event,
            context = context,
            template = template,
            renderer = htmlNotificationEventRenderer,
        )
        // Sending the message
        val sent = mailService.sendMail(
            to = config.to,
            cc = config.cc,
            subject = subject,
            body = message,
        )
        // Result
        return if (sent) {
            NotificationResult.ok(
                MailNotificationChannelOutput(
                    to = config.to,
                    cc = config.cc,
                    subject = subject,
                    body = message,
                )
            )
        } else {
            NotificationResult.error("Mail could not be sent. Check the operational logs.")
        }
    }
}