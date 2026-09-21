package net.nemerosa.ontrack.extension.jira.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
import net.nemerosa.ontrack.extension.jira.JIRAFixtures
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordFilter
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.support.client.MockRestTemplateContext
import net.nemerosa.ontrack.extension.support.client.MockRestTemplateProvider
import net.nemerosa.ontrack.extension.support.client.success
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestPropertySource(
    properties = [
        "ontrack.config.extension.support.client.resttemplate=mock",
    ]
)
class JiraLinkNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var jiraLinkNotificationChannel: JiraLinkNotificationChannel

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
    fun `Creating a link between two tickets`() {
        asAdmin {

            val jiraSourceProjectName = uid("JS")
            val jiraTargetProjectName = uid("JT")

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

                    // Setting up the Jira notification
                    eventSubscriptionService.subscribe(
                        name = "Test",
                        channel = jiraLinkNotificationChannel,
                        channelConfig = JiraLinkNotificationChannelConfig(
                            configName = configuration.name,
                            sourceQuery = """key = "$jiraSourceProjectName-123"""",
                            targetQuery = """project = "$jiraTargetProjectName" and labels = "${'$'}{project}"""",
                            linkName = "Relates",
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call(s) to Jira

                    // 1. Getting the source ticket
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/api/2/search",
                        parameters = mapOf(
                            "jql" to """key = "$jiraSourceProjectName-123"""",
                        ),
                        outcome = success(
                            mapOf(
                                "issues" to listOf(
                                    mapOf(
                                        "key" to "$jiraSourceProjectName-123",
                                    )
                                )
                            )
                        )
                    )

                    // 2. Getting the target ticket
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/api/2/search",
                        parameters = mapOf(
                            "jql" to """project = "$jiraTargetProjectName" and labels = "${project.name}"""",
                        ),
                        outcome = success(
                            mapOf(
                                "issues" to listOf(
                                    mapOf(
                                        "key" to "$jiraTargetProjectName-456",
                                    )
                                )
                            )
                        )
                    )

                    // 3. Creating the link
                    mockRestTemplateContext.onPostJson(
                        uri = "http://jira/rest/api/2/issueLink",
                        body = mapOf(
                            "type" to mapOf(
                                "name" to "Relates"
                            ),
                            "inwardIssue" to mapOf(
                                "key" to "$jiraSourceProjectName-123"
                            ),
                            "outwardIssue" to mapOf(
                                "key" to "$jiraTargetProjectName-456"
                            ),
                        ),
                        outcome = success(
                            mapOf(
                                "inwardIssue" to mapOf(
                                    "key" to "$jiraSourceProjectName-123"
                                ),
                                "outwardIssue" to mapOf(
                                    "key" to "$jiraTargetProjectName-456"
                                ),
                            )
                        )
                    )

                    build.promote(pl)

                    // Checks that the calls have been done
                    mockRestTemplateContext.verify()

                    // Checks the output of the notification (key + url)
                    assertNotNull(
                        notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                channel = "jira-link"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertNotNull(record.result.output) { output ->
                            assertEquals(
                                output.getRequiredTextField("sourceTicket"),
                                "$jiraSourceProjectName-123"
                            )
                            assertEquals(
                                output.getRequiredTextField("targetTicket"),
                                "$jiraTargetProjectName-456"
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Source ticket not found`() {
        asAdmin {

            val jiraSourceProjectName = uid("JS")
            val jiraTargetProjectName = uid("JT")

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

                    // Setting up the Jira notification
                    eventSubscriptionService.subscribe(
                        name = "Test",
                        channel = jiraLinkNotificationChannel,
                        channelConfig = JiraLinkNotificationChannelConfig(
                            configName = configuration.name,
                            sourceQuery = """key = "$jiraSourceProjectName-123"""",
                            targetQuery = """project = "$jiraTargetProjectName" and labels = "${'$'}{project}"""",
                            linkName = "Relates",
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call(s) to Jira

                    // 1. Getting the source ticket
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/api/2/search",
                        parameters = mapOf(
                            "jql" to """key = "$jiraSourceProjectName-123"""",
                        ),
                        outcome = success(
                            mapOf(
                                "issues" to emptyList<JsonNode>()
                            )
                        )
                    )

                    build.promote(pl)

                    // Checks that the calls have been done
                    mockRestTemplateContext.verify()

                    // Checks the output of the notification (key + url)
                    assertNotNull(
                        notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                channel = "jira-link"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertEquals(
                            NotificationResultType.ERROR,
                            record.result.type
                        )
                        assertEquals(
                            """No ticket found for key = "$jiraSourceProjectName-123"""",
                            record.result.message
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Too many ticket founds`() {
        asAdmin {

            val jiraSourceProjectName = uid("JS")
            val jiraTargetProjectName = uid("JT")

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

                    // Setting up the Jira notification
                    eventSubscriptionService.subscribe(
                        name = "Test",
                        channel = jiraLinkNotificationChannel,
                        channelConfig = JiraLinkNotificationChannelConfig(
                            configName = configuration.name,
                            sourceQuery = """key = "$jiraSourceProjectName-123"""",
                            targetQuery = """project = "$jiraTargetProjectName" and labels = "${'$'}{project}"""",
                            linkName = "Relates",
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call(s) to Jira

                    // 1. Getting the source ticket
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/api/2/search",
                        parameters = mapOf(
                            "jql" to """key = "$jiraSourceProjectName-123"""",
                        ),
                        outcome = success(
                            mapOf(
                                "issues" to listOf(
                                    mapOf(
                                        "key" to "$jiraSourceProjectName-123",
                                    )
                                )
                            )
                        )
                    )

                    // 2. Getting the target ticket
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/api/2/search",
                        parameters = mapOf(
                            "jql" to """project = "$jiraTargetProjectName" and labels = "${project.name}"""",
                        ),
                        outcome = success(
                            mapOf(
                                "issues" to listOf(
                                    mapOf(
                                        "key" to "$jiraTargetProjectName-456",
                                    ),
                                    mapOf(
                                        "key" to "$jiraTargetProjectName-789",
                                    ),
                                )
                            )
                        )
                    )

                    build.promote(pl)

                    // Checks that the calls have been done
                    mockRestTemplateContext.verify()

                    // Checks the output of the notification (key + url)
                    assertNotNull(
                        notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                channel = "jira-link"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertEquals(
                            NotificationResultType.ERROR,
                            record.result.type
                        )
                        assertEquals(
                            """Too many tickets found for project = "$jiraTargetProjectName" and labels = "${project.name}": [$jiraTargetProjectName-456, $jiraTargetProjectName-789]""",
                            record.result.message
                        )
                    }
                }
            }
        }
    }

}