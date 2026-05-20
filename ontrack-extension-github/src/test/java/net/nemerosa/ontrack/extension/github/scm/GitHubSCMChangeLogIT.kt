package net.nemerosa.ontrack.extension.github.scm

import net.nemerosa.ontrack.extension.git.property.GitCommitProperty
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.extension.github.TestOnGitHub
import net.nemerosa.ontrack.extension.github.githubTestEnv
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.service.SCMExtension
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestOnGitHub
class GitHubSCMChangeLogIT : AbstractGitHubTestSupport() {

    @Autowired
    @Qualifier("gitHubSCMExtension")
    private lateinit var scmExtension: SCMExtension

    @Test
    fun `GitHub SCM for all commits`() {
        asAdmin {
            project {
                gitHubRealConfig()
                val scm = scmExtension.getSCM(this)
                if (scm is SCMChangeLogEnabled) {
                    val messages = mutableListOf<String>()
                    scm.forAllCommits(this) { commit ->
                        messages += commit.message
                    }
                    assertTrue(messages.isNotEmpty(), "At least one commit must be found")
                    assertContains(messages, githubTestEnv.issues.messages.first())
                }
            }
        }
    }

    @Test
    fun `Getting a change log for GitHub using GraphQL`() {
        asAdmin {
            project {
                gitHubRealConfig()
                branch {
                    gitRealConfig()
                    val from = build {
                        setProperty(
                            this,
                            GitCommitPropertyType::class.java,
                            GitCommitProperty(githubTestEnv.issues.from)
                        )
                    }
                    val to = build {
                        setProperty(
                            this,
                            GitCommitPropertyType::class.java,
                            GitCommitProperty(githubTestEnv.issues.to)
                        )
                    }

                    run(
                        """
                            query GitHubChangeLog {
                                scmChangeLog(from: ${from.id}, to: ${to.id}) {
                                    commits {
                                        annotatedMessage
                                        commit {
                                            message
                                        }
                                    }
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
                        val changeLog = data.path("scmChangeLog")
                        // List of commit messages
                        assertEquals(
                            githubTestEnv.issues.messages,
                            changeLog.path("commits").map {
                                it.path("commit").path("message").asText()
                            }
                        )
                        // Commit annotated message
                        val issueLink =
                            "https://github.com/${githubTestEnv.organization}/${githubTestEnv.repository}/issues/${githubTestEnv.issues.issue}"
                        val commitAnnotatedMessage = changeLog.path("commits").path(0)
                            .path("annotatedMessage").asText()
                        assertContains(
                            commitAnnotatedMessage,
                            """<a href="$issueLink">#${githubTestEnv.issues.issue}</a>"""
                        )
                        // Issue service ID
                        assertEquals(
                            "github",
                            changeLog.path("issues")
                                .path("issueServiceConfiguration")
                                .path("serviceId")
                                .asText()
                        )
                        // Only one issue
                        val issue = changeLog.path("issues")
                            .path("issues").path(0)
                        assertJsonNotNull(issue)
                        // Display key, summary & URL
                        assertEquals("#${githubTestEnv.issues.issue}", issue.path("displayKey").asText())
                        assertEquals(githubTestEnv.issues.issueSummary, issue.path("summary").asText())
                        assertEquals(issueLink, issue.path("url").asText())
                        // Status name
                        assertEquals("open", issue.path("status").path("name").asText())
                        // Labels
                        assertEquals(
                            githubTestEnv.issues.issueLabels,
                            issue.path("rawIssue").path("labels").map {
                                it.path("name").asText()
                            }
                        )
                        // Milestone
                        assertEquals(
                            githubTestEnv.issues.milestone,
                            issue.path("rawIssue")
                                .path("milestone")
                                .path("title")
                                .asText()
                        )
                        assertEquals(
                            "open",
                            issue.path("rawIssue")
                                .path("milestone")
                                .path("state")
                                .asText()
                        )
                    }
                }
            }
        }
    }

}