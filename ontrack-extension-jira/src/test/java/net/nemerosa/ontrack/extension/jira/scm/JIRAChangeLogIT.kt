package net.nemerosa.ontrack.extension.jira.scm

import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
import net.nemerosa.ontrack.extension.jira.UseJiraClientMock
import net.nemerosa.ontrack.extension.jira.mock.MockJIRAInstance
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@UseJiraClientMock
class JIRAChangeLogIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var jiraConfigurationService: JIRAConfigurationService

    @Autowired
    private lateinit var mockJIRAInstance: MockJIRAInstance

    @Test
    fun `Mock JIRA issues in a change log`() {
        asAdmin {

            val config = JIRAConfiguration(
                name = TestUtils.uid("mock-"),
                url = "https://jira.nemerosa.net",
                user = null,
                password = null,
                include = emptyList(),
                exclude = emptyList(),
            )

            mockJIRAInstance.registerIssue(
                key = "ISS-20",
                summary = "Last issue before the change log",
                type = "defect"
            )
            mockJIRAInstance.registerIssue(
                key = "ISS-21",
                summary = "Some new feature",
                type = "feature"
            )
            mockJIRAInstance.registerIssue(
                key = "ISS-22",
                summary = "Some fixes are needed",
                type = "defect"
            )
            mockJIRAInstance.registerIssue(
                key = "ISS-23",
                summary = "Some nicer UI",
                type = "enhancement"
            )

            jiraConfigurationService.newConfiguration(config)

            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch(
                            issueServiceIdentifier = "jira//${config.name}",
                            scmBranch = "release/1.26",
                        )
                        val from = build("1.26.0") {
                            withRepositoryCommit("ISS-20 Last commit before the change log")
                        }
                        build("1.26.1") {
                            withRepositoryCommit("ISS-21 Some commits for a feature", property = false)
                            withRepositoryCommit("ISS-21 Some fixes for a feature")
                        }
                        build("1.26.2") {
                            withRepositoryCommit("ISS-22 Fixing some bugs")
                        }
                        val to = build("1.26.3") {
                            withRepositoryCommit("ISS-23 Fixing some CSS")
                        }

                        run(
                            """
                            query GitHubChangeLog {
                                scmChangeLog(from: ${from.id}, to: ${to.id}) {
                                    issues {
                                        issueServiceConfiguration {
                                            serviceId
                                        }
                                        issues {
                                            displayKey
                                            summary
                                            url
                                            status {
                                                name
                                            }
                                            rawIssue
                                        }
                                    }
                                }
                            }
                        """.trimIndent()
                        ) { data ->
                            val issuesContainer = data.path("scmChangeLog").path("issues")

                            assertEquals(
                                "jira",
                                issuesContainer
                                    .path("issueServiceConfiguration")
                                    .path("serviceId")
                                    .asText()
                            )

                            // Test the issues
                            val issues = issuesContainer.path("issues")

                            assertEquals(
                                listOf(
                                    "ISS-21" to "Some new feature",
                                    "ISS-22" to "Some fixes are needed",
                                    "ISS-23" to "Some nicer UI",
                                ),
                                issues.values().map { issue ->
                                    issue.path("displayKey").asText() to issue.path("summary").asText()
                                }
                            )

                            assertEquals(
                                (1..3).map {
                                    "unknown"
                                },
                                issues.values().map { issue ->
                                    issue.path("rawIssue").path("assignee").asText()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}