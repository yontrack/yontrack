package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannel
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.docs.getDocumentationExampleCode
import net.nemerosa.ontrack.model.docs.getFieldsDocumentation
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.hasAnnotation

class NotificationChannelsDocumentationIT : AbstractDocGenIT() {

    @Autowired
    private lateinit var notificationChannels: List<NotificationChannel<*, *>>

    @Test
    fun `Notifications list`() {

        fun getNotificationChannelFileId(channel: NotificationChannel<*, *>): String {
            return "notification-backend-${channel.type}"
        }

        fun getNotificationChannelTitle(channel: NotificationChannel<*, *>): String {
            return "${channel.displayName} (`${channel.type}`)"
        }

        fun <C, R> generateNotificationChannel(
            directoryContext: DocGenDirectoryContext,
            channel: NotificationChannel<C, R>
        ) {
            val description = getAPITypeDescription(channel::class)
            val parameters = getFieldsDocumentation(channel::class)
            val example = getDocumentationExampleCode(channel::class)

            val fileId = getNotificationChannelFileId(channel)

            directoryContext.writeFile(
                fileId = fileId,
                title = getNotificationChannelTitle(channel),
                header = description,
                fields = parameters,
                example = example,
                links = channel::class.findAnnotations(),
                linksPrefix = "../../",
                extendedConfig = { s ->
                    val output = getFieldsDocumentation(channel::class, section = "output")
                    if (output.isNotEmpty()) {
                        s.h2("Output")
                        s.writeFields(output)
                    }
                },
                extendedHeader = { s ->
                    if (channel::class.hasAnnotation<NoTemplate>()) {
                        s.note("This channel does not use the custom template.")
                    }
                }
            )
        }

        docGenSupport.inDirectory("notifications") {

            writeFile(
                fileName = "index",
            ) { s ->
                s.title("List of notification backends.")
                for (channel in notificationChannels.sortedBy { getNotificationChannelTitle(it) }) {
                    val id = getNotificationChannelFileId(channel)
                    val name = getNotificationChannelTitle(channel)
                    s.tocItem(name, fileName = "${id}.md")
                }
            }

            notificationChannels.forEach { channel ->
                generateNotificationChannel(this, channel)
            }

        }
    }

}