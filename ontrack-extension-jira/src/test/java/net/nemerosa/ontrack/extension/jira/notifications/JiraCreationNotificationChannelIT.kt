package net.nemerosa.ontrack.extension.jira.notifications

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
import net.nemerosa.ontrack.extension.jira.JIRAFixtures
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
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@QueueNoAsync
class JiraCreationNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var jiraCreationNotificationChannel: JiraCreationNotificationChannel

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
    fun `Creating a Jira ticket on a new promotion with a fix version`() {
        asAdmin {

            // Jira project name
            val jiraProjectName = uid("J")

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
                        name = "test",
                        channel = jiraCreationNotificationChannel,
                        channelConfig = JiraCreationNotificationChannelConfig(
                            configName = configuration.name,
                            useExisting = false,
                            projectName = jiraProjectName,
                            issueType = "Test",
                            labels = listOf("test", "v\${build}"),
                            fixVersion = "v\${build}",
                            assignee = "dcoraboeuf",
                            titleTemplate = "Build \${build} has been promoted to \${promotionLevel}",
                            customFields = listOf(
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
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Build \${build} has been promoted to \${promotionLevel}.",
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call to Jira
                    mockRestTemplateContext.onPostJson(
                        uri = "http://jira/rest/api/2/issue",
                        body = mapOf(
                            "fields" to mapOf(
                                "project" to mapOf(
                                    "key" to jiraProjectName,
                                ),
                                "summary" to "Build ${build.name} has been promoted to ${pl.name}",
                                "issuetype" to mapOf(
                                    "name" to "Test"
                                ),
                                "labels" to listOf("test", "v${build.name}"),
                                "description" to """
                                    Build [${build.name}|http://localhost:3000/build/${build.id}] has been promoted to [${pl.name}|http://localhost:3000/promotionLevel/${pl.id}].
                                """.trimIndent(),
                                "assignee" to mapOf(
                                    "name" to "dcoraboeuf"
                                ),
                                "fixVersions" to listOf(
                                    mapOf(
                                        "name" to "v${build.name}"
                                    )
                                ),
                                "duedate" to "2024-04-16",
                                "customfield_11000" to "Some direct value",
                                "customfield_12000" to mapOf(
                                    "value" to "Promotion level name ${pl.name}"
                                ),
                            )
                        ),
                        outcome = success(
                            mapOf(
                                "key" to "$jiraProjectName-123",
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
                                channel = "jira-creation"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertNotNull(record.result.output) { output ->
                            val key = output.getRequiredTextField("ticketKey")
                            assertEquals(
                                "http://jira/browse/$key",
                                output.getRequiredTextField("url")
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Creating a Jira ticket on a new promotion without a fix version`() {
        asAdmin {

            // Jira project name
            val jiraProjectName = uid("J")

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
                        name = "test",
                        channel = jiraCreationNotificationChannel,
                        channelConfig = JiraCreationNotificationChannelConfig(
                            configName = configuration.name,
                            useExisting = false,
                            projectName = jiraProjectName,
                            issueType = "Test",
                            labels = listOf("test", "v\${build}"),
                            fixVersion = null,
                            assignee = "dcoraboeuf",
                            titleTemplate = "Build \${build} has been promoted to \${promotionLevel}",
                            customFields = listOf(
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
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Build \${build} has been promoted to \${promotionLevel}.",
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the call to Jira
                    mockRestTemplateContext.onPostJson(
                        uri = "http://jira/rest/api/2/issue",
                        body = mapOf(
                            "fields" to mapOf(
                                "project" to mapOf(
                                    "key" to jiraProjectName,
                                ),
                                "summary" to "Build ${build.name} has been promoted to ${pl.name}",
                                "issuetype" to mapOf(
                                    "name" to "Test"
                                ),
                                "labels" to listOf("test", "v${build.name}"),
                                "description" to """
                                    Build [${build.name}|http://localhost:3000/build/${build.id}] has been promoted to [${pl.name}|http://localhost:3000/promotionLevel/${pl.id}].
                                """.trimIndent(),
                                "assignee" to mapOf(
                                    "name" to "dcoraboeuf"
                                ),
                                "duedate" to "2024-04-16",
                                "customfield_11000" to "Some direct value",
                                "customfield_12000" to mapOf(
                                    "value" to "Promotion level name ${pl.name}"
                                ),
                            )
                        ),
                        outcome = success(
                            mapOf(
                                "key" to "$jiraProjectName-123",
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
                                channel = "jira-creation"
                            )
                        ).pageItems.firstOrNull()
                    ) { record ->
                        assertNotNull(record.result.output) { output ->
                            val key = output.getRequiredTextField("ticketKey")
                            assertEquals(
                                "http://jira/browse/$key",
                                output.getRequiredTextField("url")
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Checking a Jira ticket existence on a new promotion`() {
        asAdmin {

            // Jira project name
            val jiraProjectName = uid("J")

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
                        name = "test",
                        channel = jiraCreationNotificationChannel,
                        channelConfig = JiraCreationNotificationChannelConfig(
                            useExisting = true,
                            configName = configuration.name,
                            projectName = jiraProjectName, // <-- used for identification of the ticket
                            issueType = "Service Issue", // <-- used for identification of the ticket
                            labels = listOf("test", "v\${build}"), // <-- used for identification of the ticket
                            titleTemplate = "Build \${build} has been promoted to \${promotionLevel}",
                            customFields = emptyList(),
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = "Build \${build} has been promoted to \${promotionLevel}.",
                        EventFactory.NEW_PROMOTION_RUN,
                    )

                    val build = build()

                    // Mocking the calls to GET the issue, returning one
                    val jql =
                        """project = "$jiraProjectName" AND issuetype = "Service Issue" AND labels = "test" AND labels = "v${build.name}""""
                    mockRestTemplateContext.onGetJson(
                        uri = "http://jira/rest/api/2/search",
                        parameters = mapOf(
                            "jql" to jql,
                        ),
                        outcome = success(
                            mapOf(
                                "issues" to listOf(
                                    mapOf(
                                        "key" to "$jiraProjectName-234",
                                    )
                                )
                            )
                        )
                    )

                    build.promote(pl)

                    // We expect the ticket to have been created
                    mockRestTemplateContext.verify()

                    // Checks the outputs of the notification (key + url)
                    assertEquals(
                        listOf(
                            "$jiraProjectName-234"
                        ),
                        notificationRecordingService.filter(
                            filter = NotificationRecordFilter(
                                channel = "jira-creation"
                            )
                        ).pageItems.map {
                            it.result.output?.getRequiredTextField("ticketKey")
                        }
                    )
                }
            }
        }
    }

}