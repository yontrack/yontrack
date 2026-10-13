package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.git.exceptions.GitRepositoryNoRemoteException
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Testing the behaviour of the projects when their Git remote cannot be reached.
 *
 * The failures are counted in the database, for all the replicas (#2088).
 */
class GitServiceNoRemoteIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var gitNoRemoteCounter: GitNoRemoteCounter

    @Autowired
    private lateinit var storageService: StorageService

    @Test
    fun `Disabling a project when number of attempts to sync exceeds a threshold`() {
        createRepo {
            // Creating a repository with some dummy content
            commits(1)
        } and { repo, _ ->
            project {
                // Using this repository
                gitProject(repo)
                // Gets its configuration
                val gitConfig = gitService.getProjectConfiguration(this)
                    ?: fail("We expect a Git configuration at project level.")
                // Synching the repository
                gitService.syncProjectRepository(gitConfig, this)
                // Checking that the counter has not been set at all
                assertEquals(0, gitNoRemoteCounter.getNoRemoteCount(this), "Git No Remote count has not been set")
                // Removing the remote (ie. the folder in this case)
                repo.close()
                // The failures which are accepted are counted
                repeat(gitConfigProperties.remote.maxNoRemote) { index ->
                    gitService.syncProjectRepository(gitConfig, this)
                    assertEquals(index + 1, gitNoRemoteCounter.getNoRemoteCount(this))
                    assertFalse(structureService.getProject(id).isDisabled, "Project still enabled")
                }
                // One more and the project is disabled
                gitService.syncProjectRepository(gitConfig, this)
                assertTrue(structureService.getProject(id).isDisabled, "Project has been disabled")
                // The count starts again for the project, if enabled again
                assertEquals(0, gitNoRemoteCounter.getNoRemoteCount(this), "Count reset once the project is disabled")
            }
        }
    }

    @Test
    fun `The failures counted by any replica count towards the threshold`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                gitProject(repo)
                val gitConfig = gitService.getProjectConfiguration(this)
                    ?: fail("We expect a Git configuration at project level.")
                gitService.syncProjectRepository(gitConfig, this)
                repo.close()
                // All the accepted failures but one, counted by other replicas, in the database
                repeat(gitConfigProperties.remote.maxNoRemote - 1) {
                    storageService.increment(GitNoRemoteCounter.STORE, id.toString())
                }
                // The last accepted failure, here
                gitService.syncProjectRepository(gitConfig, this)
                assertFalse(structureService.getProject(id).isDisabled, "Project still enabled")
                // One more and the project is disabled
                gitService.syncProjectRepository(gitConfig, this)
                assertTrue(structureService.getProject(id).isDisabled, "Project has been disabled")
            }
        }
    }

    @Test
    fun `The count of the failures is reset by a successful synchronisation`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                gitProject(repo)
                val gitConfig = gitService.getProjectConfiguration(this)
                    ?: fail("We expect a Git configuration at project level.")
                storageService.increment(GitNoRemoteCounter.STORE, id.toString())
                assertEquals(1, gitNoRemoteCounter.getNoRemoteCount(this))
                gitService.syncProjectRepository(gitConfig, this)
                assertEquals(0, gitNoRemoteCounter.getNoRemoteCount(this), "Count reset by the successful synchronisation")
            }
        }
    }

    @Test
    fun `Disabling a project does not happen when number of attempts to sync exceeds a threshold`() {
        val oldMaxNoRemote = gitConfigProperties.remote.maxNoRemote
        try {
            gitConfigProperties.remote.maxNoRemote = 0
            createRepo {
                // Creating a repository with some dummy content
                commits(1)
            } and { repo, _ ->
                project {
                    // Using this repository
                    gitProject(repo)
                    // Gets its configuration
                    val gitConfig = gitService.getProjectConfiguration(this)
                        ?: fail("We expect a Git configuration at project level.")
                    // Synching the repository
                    gitService.syncProjectRepository(gitConfig, this)
                    // Checking that the counter has not been set at all
                    assertEquals(0, gitNoRemoteCounter.getNoRemoteCount(this), "Git No Remote count has not been set")
                    // Removing the remote (ie. the folder in this case)
                    repo.close()
                    // Synching raises an error
                    assertFailsWith<GitRepositoryNoRemoteException> {
                        gitService.syncProjectRepository(gitConfig, this)
                    }
                }
            }
        } finally {
            gitConfigProperties.remote.maxNoRemote = oldMaxNoRemote
        }
    }

}