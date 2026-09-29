package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurator
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@WithGitPullRequestEnabled
class GitPullRequestInformationExtensionIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var gitMockingConfigurator: GitMockingConfigurator

    @Autowired
    private lateinit var entityInformationExtension: GitPullRequestInformationExtension

    @BeforeEach
    fun init() {
        gitMockingConfigurator.clearPullRequests()
    }

    @Test
    fun `No PR entity information for a normal branch`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("release/1.0")
                    // Gets the PR entity information
                    val info = entityInformationExtension.getInformation(this)
                    assertNull(info, "No extra PR entity information")
                }
            }
        }
    }

    @Test
    fun `PR entity information for a PR branch`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            // Registers this PR in mock service
            gitMockingConfigurator.registerPullRequest(1, title = "Useful feature")
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    // Gets the PR entity information
                    val info = entityInformationExtension.getInformation(this)
                    assertNotNull(info) {
                        assertIs<GitPullRequestInformationExtension.GitPullRequestInformationExtensionData>(it.data) { pr ->
                            assertEquals(1, pr.pr.id)
                            assertEquals(true, pr.pr.isValid)
                            assertEquals("#1", pr.pr.key)
                            assertEquals("feature/TK-1-feature", pr.pr.source)
                            assertEquals("release/1.0", pr.pr.target)
                            assertEquals("Useful feature", pr.pr.title)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `PR entity information for a PR branch with unexisting PR`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    // Gets the PR entity information
                    val info = entityInformationExtension.getInformation(this)
                    assertNotNull(info) {
                        assertIs<GitPullRequestInformationExtension.GitPullRequestInformationExtensionData>(it.data) { pr ->
                            assertEquals(1, pr.pr.id)
                            assertEquals(false, pr.pr.isValid)
                            assertEquals("#1", pr.pr.key)
                            assertEquals("", pr.pr.source)
                            assertEquals("", pr.pr.target)
                            assertEquals("", pr.pr.title)
                        }
                    }
                }
            }
        }
    }
}