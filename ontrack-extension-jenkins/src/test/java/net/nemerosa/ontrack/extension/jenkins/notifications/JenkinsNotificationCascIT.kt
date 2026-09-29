package net.nemerosa.ontrack.extension.jenkins.notifications

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionFilter
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Disabled("Flaky")
class JenkinsNotificationCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var jenkinsConfigurationService: JenkinsConfigurationService

    @Autowired
    protected lateinit var eventSubscriptionService: EventSubscriptionService

    @Test
    fun `Notification for a promotion run at promotion level level`() {
        asAdmin {
            withDisabledConfigurationTest {
                val config = JenkinsConfiguration(
                    name = uid("jc"),
                    url = "https://jenkins",
                    user = "username",
                    password = "password",
                )
                jenkinsConfigurationService.newConfiguration(config)
                project {
                    branch {
                        val pl = promotionLevel()
                        casc(
                            """
                            ontrack:
                              extensions:
                                notifications:
                                  entity-subscriptions:
                                    - entity:
                                        project: ${project.name}
                                        branch: $name
                                        promotion: ${pl.name}
                                      subscriptions:
                                        - name: test
                                          channel: jenkins
                                          channel-config:
                                            config: ${config.name}
                                            job: path/to/pipeline
                                            parameters:
                                              - name: PROMOTION
                                                value: "{Promotion_level}"
                                          events:
                                            - new_promotion_run
                        """.trimIndent()
                        )
                        // Gets the subscription
                        val subscriptions = eventSubscriptionService.filterSubscriptions(
                            EventSubscriptionFilter(
                                entity = pl.toProjectEntityID(),
                                origin = "casc",
                            )
                        )
                        assertNotNull(
                            subscriptions.pageItems.firstOrNull(),
                            "Subscription has been created"
                        ) { subscription ->
                            assertEquals("jenkins", subscription.channel)
                            val nc = subscription.channelConfig.parse<JenkinsNotificationChannelConfig>()
                            assertEquals(config.name, nc.config)
                            assertEquals("path/to/pipeline", nc.job)
                            assertEquals(
                                listOf(
                                    JenkinsNotificationChannelConfigParam("PROMOTION", "{Promotion_level}")
                                ),
                                nc.parameters
                            )
                            assertEquals(JenkinsNotificationChannelConfigCallMode.ASYNC, nc.callMode)
                        }

                    }
                }
            }
        }
    }

    @Test
    fun `Notification for a promotion run at branch level using keywords`() {
        asAdmin {
            withDisabledConfigurationTest {
                val config = JenkinsConfiguration(
                    name = uid("jc"),
                    url = "https://jenkins",
                    user = "username",
                    password = "password",
                )
                jenkinsConfigurationService.newConfiguration(config)
                project {
                    branch {
                        promotionLevel()
                        casc(
                            """
                            ontrack:
                              extensions:
                                notifications:
                                  entity-subscriptions:
                                    - entity:
                                        project: ${project.name}
                                        branch: $name
                                      subscriptions:
                                        - name: test
                                          channel: jenkins
                                          channel-config:
                                            config: ${config.name}
                                            job: path/to/pipeline
                                            parameters:
                                              - name: PROMOTION
                                                value: "{Promotion}"
                                          events:
                                            - new_promotion_run
                        """.trimIndent()
                        )
                        // Gets the subscription
                        val subscriptions = eventSubscriptionService.filterSubscriptions(
                            EventSubscriptionFilter(
                                entity = this.toProjectEntityID(),
                                origin = "casc",
                            )
                        )
                        assertNotNull(
                            subscriptions.pageItems.firstOrNull(),
                            "Subscription has been created"
                        ) { subscription ->
                            assertEquals("jenkins", subscription.channel)
                            val nc = subscription.channelConfig.parse<JenkinsNotificationChannelConfig>()
                            assertEquals(config.name, nc.config)
                            assertEquals("path/to/pipeline", nc.job)
                            assertEquals(
                                listOf(
                                    JenkinsNotificationChannelConfigParam("PROMOTION", "{Promotion}")
                                ),
                                nc.parameters
                            )
                            assertEquals(JenkinsNotificationChannelConfigCallMode.ASYNC, nc.callMode)
                        }

                    }
                }
            }
        }
    }

}