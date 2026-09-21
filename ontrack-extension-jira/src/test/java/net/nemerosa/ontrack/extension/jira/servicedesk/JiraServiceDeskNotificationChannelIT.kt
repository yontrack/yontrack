package net.nemerosa.ontrack.extension.jira.servicedesk

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
import net.nemerosa.ontrack.extension.jira.JIRAFixtures
import net.nemerosa.ontrack.extension.jira.notifications.JiraCustomField
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordFilter
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.support.client.MockRestTemplateContext
import net.nemerosa.ontrack.extension.support.client.MockRestTemplateProvider
import net.nemerosa.ontrack.extension.support.client.success
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.events.EventFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@QueueNoAsync
class JiraServiceDeskNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var jiraServiceDeskNotificationChannel: JiraServiceDeskNotificationChannel

    @Autowired
    private lateinit var jiraConfigurationService: JIRAConfigurationService

    @Autowired
    private lateinit var mockRestTemplateProvider: MockRestTemplateProvider
    private lateinit var mockRestTemplateContext: MockRestTemplateContext

    @Autowired
    private lateinit var notificationRecordingService: NotificationRecordingService

    @BeforeEach
    fun init() {
        mockRestTemplateContext = mockRestTemplateProvider.createSession()
        asAdmin {
            notificationRecordingService.clearAll()
        }
    }

    @AfterEach
    fun close() {
        mockRestTemplateContext.close()
    }

    @Test
    fun `Creating a Jira service desk ticket on a new promotion without checking existing requests`() {
        asAdmin {

            // Service desk & request type
            val serviceDeskId = Random.nextInt()
            val requestTypeId = Random.nextInt()

            // Jira configuration
            val configuration = JIRAFixtures.jiraConfiguration()
            withDisabledConfigurationTest {
                jiraConfigurationService.newConfiguration(
                    configuration
                )
            }

            project {
                branch {
                    val pl = promotionLevel()

                    // Setting up the Jira service desk notification
                    eventSubscriptionService.subscribe(
                        name = "test",
                        channel = jiraServiceDeskNotificationChannel,
                        channelConfig = JiraServiceDeskNotificationChannelConfig(
                            configName = configuration.name,
                            useExisting = false,
                            serviceDeskId = serviceDeskId,
                            requestTypeId = requestTypeId,
                            fields = listOf(
                                JiraCustomField(
                                    "summary",
                                    StringNode("Build \${build} has been promoted to \${promotionLevel}")
                                ),
                                JiraCustomField(
                                    "description",
                                    StringNode("Build \${build} has been promoted to \${promotionLevel}.")
                                ),
                                JiraCustomField(
                                    "duedate",
                                    StringNode("2024-04-16")
                                ),
                                JiraCustomField(
                                    "customfield_11000",
                                    StringNode("Some direct value")
                                ),
                                JiraCustomField(
                                    "customfield_12000",
                                    mapOf(
                                        "value" to "Promotion level name \${promotionLevel}",
                                    ).asJson()
                                ),
                            ),
                            searchTerm = "Build \${build} has been promoted to \${promotionLevel}"
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call to Jira
                    mockRestTemplateContext.onPostJson(
                        uri = "http://jira/rest/servicedeskapi/request",
                        body = mapOf(
                            "serviceDeskId" to serviceDeskId,
                            "requestTypeId" to requestTypeId,
                            "requestFieldValues" to mapOf(
                                "summary" to "Build ${build.name} has been promoted to ${pl.name}",
                                "description" to """
                                    Build [${build.name}|http://localhost:3000/build/${build.id}] has been promoted to [${pl.name}|http://localhost:3000/promotionLevel/${pl.id}].
                                """.trimIndent(),
                                "duedate" to "2024-04-16",
                                "customfield_11000" to "Some direct value",
                                "customfield_12000" to mapOf(
                                    "value" to "Promotion level name ${pl.name}",
                                ),
                            ),
                        ),
                        outcome = success(
                            mapOf(
                                "issueKey" to "SD-123",
                                "_links" to mapOf(
                                    "web" to "http://jira/sd/SD-123",
                                )
                            )
                        )
                    )

                    build.promote(pl)

                    // Checks that the call has been done
                    mockRestTemplateContext.verify()

                    // Checks the output of the notification (key + url)
                    assertNotNull(
                        notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                channel = "jira-service-desk"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertNotNull(record.result.output) { output ->
                            val key = output.getRequiredTextField("ticketKey")
                            assertEquals(
                                "http://jira/sd/$key",
                                output.getRequiredTextField("url")
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a Jira service desk ticket on a new promotion with an existing request`() {
        asAdmin {

            // Service desk & request type
            val serviceDeskId = Random.nextInt()
            val requestTypeId = Random.nextInt()

            // Jira configuration
            val configuration = JIRAFixtures.jiraConfiguration()
            withDisabledConfigurationTest {
                jiraConfigurationService.newConfiguration(
                    configuration
                )
            }

            project {
                branch {
                    val pl = promotionLevel()

                    // Setting up the Jira service desk notification
                    eventSubscriptionService.subscribe(
                        name = "test",
                        channel = jiraServiceDeskNotificationChannel,
                        channelConfig = JiraServiceDeskNotificationChannelConfig(
                            configName = configuration.name,
                            useExisting = true,
                            serviceDeskId = serviceDeskId,
                            requestTypeId = requestTypeId,
                            fields = listOf(
                                JiraCustomField(
                                    "summary",
                                    StringNode("Build \${build} has been promoted to \${promotionLevel}")
                                ),
                                JiraCustomField(
                                    "description",
                                    StringNode("Build \${build} has been promoted to \${promotionLevel}.")
                                ),
                                JiraCustomField(
                                    "duedate",
                                    StringNode("2024-04-16")
                                ),
                                JiraCustomField(
                                    "customfield_11000",
                                    StringNode("Some direct value")
                                ),
                                JiraCustomField(
                                    "customfield_12000",
                                    mapOf(
                                        "value" to "Promotion level name \${promotionLevel}",
                                    ).asJson()
                                ),
                            ),
                            searchTerm = "Build \${build} has been promoted to \${promotionLevel}"
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call to Jira
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/servicedeskapi/request",
                        parameters = mapOf(
                            "serviceDeskId" to serviceDeskId.toString(),
                            "requestTypeId" to requestTypeId.toString(),
                            "requestStatus" to "ALL_REQUESTS",
                            "searchTerm" to "Build ${build.name} has been promoted to ${pl.name}",
                        ),
                        outcome = success(
                            mapOf(
                                "values" to listOf(
                                    mapOf(
                                        "issueKey" to "SD-123",
                                        "_links" to mapOf(
                                            "web" to "http://jira/sd/SD-123",
                                        )
                                    )
                                )
                            )
                        )
                    )

                    build.promote(pl)

                    // Checks that the call has been done
                    mockRestTemplateContext.verify()

                    // Checks the output of the notification (key + url)
                    assertNotNull(
                        notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                channel = "jira-service-desk"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertNotNull(record.result.output) { output ->
                            val key = output.getRequiredTextField("ticketKey")
                            assertEquals(
                                "http://jira/sd/$key",
                                output.getRequiredTextField("url")
                            )
                        }
                    }
                }
            }
        }
    }

}