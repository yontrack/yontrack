package net.nemerosa.ontrack.extension.gitlab.scm

import net.nemerosa.ontrack.extension.gitlab.AbstractGitLabTestSupport
import net.nemerosa.ontrack.extension.gitlab.GitLabIssueServiceExtension
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssueServiceConfiguration
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The GitLab SCM against a real Yontrack instance: what a configured GitLab project gives, and that the
 * generic SCM lookup finds it.
 */
class GitLabSCMExtensionIT : AbstractGitLabTestSupport() {

    @Autowired
    private lateinit var extension: GitLabSCMExtension

    @Autowired
    private lateinit var scmDetector: SCMDetector

    @Test
    fun `A project with no GitLab property has no GitLab SCM`() {
        asAdmin {
            project {
                assertNull(extension.getSCM(this))
            }
        }
    }

    @Test
    fun `A configured GitLab project has an SCM able to compute a change log`() {
        asAdmin {
            val config = gitLabConfig()
            val repository = "group/subgroup/${uid("p")}"
            project {
                setGitLabProperty(config.name, repository)
                val scm = extension.getSCM(this)
                assertNotNull(scm) {
                    assertEquals("git", it.type)
                    assertEquals("gitlab", it.engine)
                    assertEquals(repository, it.repository)
                    assertEquals("https://gitlab.com/nemerosa/test/$repository", it.repositoryHtmlURL)
                    assertEquals("https://gitlab.com/nemerosa/test/$repository.git", it.repositoryURI)
                    assertIs<SCMChangeLogEnabled>(it)
                }
            }
        }
    }

    @Test
    fun `The generic SCM lookup finds the GitLab SCM of a project`() {
        asAdmin {
            val config = gitLabConfig()
            val repository = "group/${uid("p")}"
            project {
                setGitLabProperty(config.name, repository)
                val scm = scmDetector.getSCM(this)
                assertNotNull(scm) {
                    assertEquals("gitlab", it.engine)
                    assertEquals(repository, it.repository)
                }
            }
        }
    }

    @Test
    fun `The issue service of a GitLab SCM defaults to the project's own GitLab issues`() {
        asAdmin {
            val config = gitLabConfig()
            val repository = "group/${uid("p")}"
            project {
                setGitLabProperty(config.name, repository)
                val scm = extension.getSCM(this) as SCMChangeLogEnabled
                val configuredIssueService = scm.getConfiguredIssueService()
                assertNotNull(configuredIssueService) {
                    assertEquals(
                        GitLabIssueServiceExtension.GITLAB_SERVICE_ID,
                        it.issueServiceExtension.id,
                    )
                    val issueServiceConfiguration = it.issueServiceConfiguration
                    assertIs<GitLabIssueServiceConfiguration>(issueServiceConfiguration)
                    assertEquals(repository, issueServiceConfiguration.repository)
                }
                assertEquals("gitlab", scm.issueRepositoryContext.repositoryType)
                assertEquals(repository, scm.issueRepositoryContext.repositoryName)
            }
        }
    }

    @Test
    fun `A reference on a configuration which does not exist is null`() {
        asAdmin {
            assertNull(extension.getSCMPath(uid("nope"), "group/project/ontrack.yaml"))
        }
    }
}
