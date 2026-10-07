package net.nemerosa.ontrack.extension.notifications.mail

import com.icegreen.greenmail.util.GreenMailUtil
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class MailChannelIT : AbstractMailTestSupport() {

    @Autowired
    private lateinit var mailNotificationChannel: MailNotificationChannel

    @Test
    fun `Mail notification for a promotion`() {
        val name = uid("s")
        val subject = "Mail notification $name"
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    // Listening to events on this promotion
                    eventSubscriptionService.subscribe(
                        name = uid("n-"),
                        channel = mailNotificationChannel,
                        channelConfig = MailNotificationChannelConfig(
                            to = DEFAULT_ADDRESS,
                            cc = null,
                            subject = "Mail notification $name"
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    // Promotion
                    build {
                        promote(pl)
                        // Checks that a mail has been received
                        val messages = greenMail.receivedMessages
                        val mail = messages.find {
                            it.subject == subject
                        }
                        if (mail != null) {
                            assertEquals(
                                """Build <a href="http://localhost:3000/build/${this.id}">${this.name}</a> has been promoted to <a href="http://localhost:3000/promotionLevel/${pl.id}">${pl.name}</a> for branch <a href="http://localhost:3000/branch/${branch.id}">${branch.name}</a> in <a href="http://localhost:3000/project/${project.id}">${project.name}</a>.""",
                                GreenMailUtil.getBody(mail)
                            )
                        } else {
                            fail(
                                """
                                    Mail was not found, but the following mails were available:
                                    
                                    ${messages.joinToString("\n") { it.subject }}
                                """.trimIndent()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Mail notification for a promotion with a custom simple template`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    // Listening to events on this promotion
                    eventSubscriptionService.subscribe(
                        name = uid("n-"),
                        channel = mailNotificationChannel,
                        channelConfig = MailNotificationChannelConfig(
                            to = DEFAULT_ADDRESS,
                            cc = null,
                            subject = "Released ${'$'}{project} ${'$'}{build}"
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = """
                            ${'$'}{project} ${'$'}{build} has been released.
                            
                            It's actually been promoted to ${'$'}{promotionLevel}.
                        """.trimIndent(),
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    // Promotion
                    build {
                        promote(pl)
                        // Checks that a mail has been received
                        val messages = greenMail.receivedMessages
                        val message = messages.find {
                            it.subject == "Released ${project.name} $name"
                        }
                        if (message != null) {
                            assertEquals(
                                """
                                    <a href="http://localhost:3000/project/${project.id}">${project.name}</a> <a href="http://localhost:3000/build/$id">$name</a> has been released.
                                    
                                    It's actually been promoted to <a href="http://localhost:3000/promotionLevel/${pl.id}">${pl.name}</a>.
                                """.trimIndent()
                                    .lines().map { it.trim() },
                                GreenMailUtil.getBody(message)
                                    .lines().map { it.trim() }
                            )
                        } else {
                            fail(
                                """
                                    Mail was not found, but the following mails were available:
                                    
                                    ${messages.joinToString("\n") { it.subject }}
                                """.trimIndent()
                            )
                        }
                    }
                }
            }
        }
    }


    @Test
    fun `Agents never receive mail`() {
        val name = uid("s")
        val subject = "Mail without agents $name"
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    eventSubscriptionService.subscribe(
                        name = uid("n-"),
                        channel = mailNotificationChannel,
                        channelConfig = MailNotificationChannelConfig(
                            to = "claude-code-ci[agent], $DEFAULT_ADDRESS",
                            cc = "codex[agent]",
                            subject = subject,
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    build {
                        promote(pl)
                        val mail = greenMail.receivedMessages.find { it.subject == subject }
                            ?: fail("Mail was not sent to the person")
                        assertEquals(
                            listOf(DEFAULT_ADDRESS),
                            mail.allRecipients.map { it.toString() },
                            "Only the person receives the mail"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `No mail at all when only agents are recipients`() {
        val name = uid("s")
        val subject = "Mail to agents only $name"
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    eventSubscriptionService.subscribe(
                        name = uid("n-"),
                        channel = mailNotificationChannel,
                        channelConfig = MailNotificationChannelConfig(
                            to = "claude-code-ci[agent]",
                            cc = null,
                            subject = subject,
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    build {
                        promote(pl)
                        assertNull(
                            greenMail.receivedMessages.find { it.subject == subject },
                            "No mail sent to agents"
                        )
                    }
                }
            }
        }
    }

}