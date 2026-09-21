package net.nemerosa.ontrack.extension.jira.client

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.extension.jira.notifications.JiraCustomField
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestOnJiraServer
class JiraClientRealIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var jiraClientTestSupport: JiraClientTestSupport

    @Test
    fun `Creating an issue`() {
        asAdmin {
            jiraClientTestSupport.withRealJiraClient { client, config ->
                val (key, url) = client.createIssue(
                    configuration = config,
                    project = jiraClientEnv.project,
                    issueType = jiraClientEnv.issueType,
                    labels = listOf(
                        "sample-label"
                    ),
                    fixVersion = null,
                    assignee = "admin",
                    title = "Sample title",
                    body = """
                        h3. Sample header
                        
                        *Sample* description
                        
                        [Sample link|https://github.com/nemerosa/ontrack]
                    """.trimIndent(),
                    customFields = listOf(
                        JiraCustomField(
                            name = "duedate",
                            value = StringNode("2024-04-18")
                        ),
                    )
                )
                assertTrue(key.isNotBlank())
                assertEquals(
                    "${jiraClientEnv.url}/browse/$key",
                    url
                )

                // Getting the issue back
                assertNotNull(client.getIssue(key, config)) { issue ->
                    assertEquals("Sample title", issue.summary)
                }
            }
        }
    }

    @Test
    fun `Linking issues`() {
        asAdmin {
            jiraClientTestSupport.withRealJiraClient { client, config ->
                val (key1) = client.createIssue(
                    configuration = config,
                    project = jiraClientEnv.project,
                    issueType = jiraClientEnv.issueType,
                    title = "Issue 1",
                    body = "Description 1",
                )
                val (key2) = client.createIssue(
                    configuration = config,
                    project = jiraClientEnv.project,
                    issueType = jiraClientEnv.issueType,
                    title = "Issue 2",
                    body = "Description 2",
                )
                client.createLink(
                    jiraConfiguration = config,
                    sourceTicket = key1,
                    targetTicket = key2,
                    linkName = "Relates",
                )

                // Checking the links
                assertNotNull(client.getIssue(key1, config)) { issue ->
                    assertEquals(
                        listOf(key2),
                        issue.links.map { it.key }
                    )
                }
            }
        }
    }

    @Test
    fun `Looking for an issue`() {
        asAdmin {
            jiraClientTestSupport.withRealJiraClient { client, config ->
                // Unique label
                val label = uid("test-")
                // Creating the issue
                val (key, _) = client.createIssue(
                    configuration = config,
                    project = jiraClientEnv.project,
                    issueType = jiraClientEnv.issueType,
                    labels = listOf(label),
                    fixVersion = null,
                    assignee = null,
                    title = "Sample title",
                    body = """
                        Sample description
                    """.trimIndent(),
                    customFields = emptyList(),
                )

                // JQL
                val jql =
                    """project = ${jiraClientEnv.project} AND issuetype = ${jiraClientEnv.issueType} AND labels ="$label""""
                val keys = client.searchIssueStubs(config, jql).map { it.key }
                assertEquals(
                    listOf(key),
                    keys
                )
            }
        }
    }

}