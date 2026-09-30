package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Interacting with pull requests
 */
@WithGitPullRequestEnabled
class GitPullRequestIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var gitMockingConfigurator: GitMockingConfigurator

    @BeforeEach
    fun init() {
        gitMockingConfigurator.clearPullRequests()
    }

    @Test
    fun `Branch configuration for a normal branch is not marked as pull request`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                prGitProject()
                branch {
                    gitBranch("release/1.0")
                    // Gets the Git configuration for this branch
                    val pr = gitService.getBranchAsPullRequest(this)
                    assertNull(pr, "Not a PR")
                }
            }
        }
    }

    @Test
    fun `Branch configuration for a PR is marked as pull request`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            gitMockingConfigurator.registerPullRequest(1, title = "Useful feature")
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    // Registers this PR in mock service
                    // Gets the Git configuration for this branch
                    val pr = gitService.getBranchAsPullRequest(this)
                    assertNotNull(pr) {
                        assertEquals(1, it.id)
                        assertEquals(true, it.isValid)
                        assertEquals("#1", it.key)
                        assertEquals("feature/TK-1-feature", it.source)
                        assertEquals("release/1.0", it.target)
                        assertEquals("Useful feature", it.title)
                    }
                }
            }
        }
    }

    @Test
    fun `PR read from the cache once fetched from the SCM`() {
        createRepo {
            commits(1)
        } and { _, _ ->
            gitMockingConfigurator.registerPullRequest(1, title = "Useful feature")
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    // First call, fetched from the SCM
                    assertNotNull(gitService.getBranchAsPullRequest(this), "PR fetched from the SCM")
                    // The PR is gone from the SCM, but still in the cache
                    gitMockingConfigurator.unregisterPullRequest(1)
                    val pr = gitService.getBranchAsPullRequest(this)
                    assertNotNull(pr, "PR read from the cache") {
                        assertEquals(1, it.id)
                        assertEquals(true, it.isValid)
                        assertEquals("#1", it.key)
                        assertEquals("feature/TK-1-feature", it.source)
                        assertEquals("release/1.0", it.target)
                        assertEquals("Useful feature", it.title)
                        assertEquals("open", it.status)
                        assertEquals("uri:testing:web:git:pr:1", it.url)
                    }
                }
            }
        }
    }

    @Test
    fun `Invalid PR read from the cache stays invalid`() {
        createRepo {
            commits(1)
        } and { _, _ ->
            gitMockingConfigurator.registerPullRequest(1, invalid = true)
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    assertNotNull(gitService.getBranchAsPullRequest(this), "PR fetched from the SCM") {
                        assertEquals(false, it.isValid)
                    }
                    gitMockingConfigurator.unregisterPullRequest(1)
                    assertNotNull(gitService.getBranchAsPullRequest(this), "PR read from the cache") {
                        assertEquals(1, it.id)
                        assertEquals(false, it.isValid)
                        assertEquals("#1", it.key)
                    }
                }
            }
        }
    }

    @Test
    fun `PR always fetched from the SCM when the cache is disabled`() {
        createRepo {
            commits(1)
        } and { _, _ ->
            gitMockingConfigurator.registerPullRequest(1, title = "Useful feature")
            project {
                prGitProject()
                branch {
                    gitBranch("PR-1")
                    withPRCacheDisabled {
                        assertNotNull(gitService.getBranchAsPullRequest(this), "PR fetched from the SCM") {
                            assertEquals("Useful feature", it.title)
                        }
                        // The PR changes in the SCM
                        gitMockingConfigurator.registerPullRequest(1, title = "Renamed feature")
                        assertNotNull(gitService.getBranchAsPullRequest(this), "PR fetched again from the SCM") {
                            assertEquals("Renamed feature", it.title)
                        }
                    }
                }
            }
        }
    }

}
