package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.extension.git.model.GitSynchronisationRequest
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.git.model.GitSynchronisationStatus
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.job.JobScheduler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The synchronisation of a project is the same on every replica (#2085): its status is the shared
 * status of the indexation job, and a reset bumps the reset generation of the repository.
 */
@OptIn(ExperimentalTime::class)
class GitProjectSyncIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var gitCloneGenerationService: GitCloneGenerationService

    @Test
    fun `the synchronisation status is the status of the indexation job`() {
        createRepo {
            commit(1)
            git("checkout", "-b", "feature")
            commit(2)
        } and { repo, _ ->
            project {
                gitProject(repo, sync = false)
                asAdmin().execute { jobScheduler.refresh() }

                gitService.getProjectGitSyncInfo(this).apply {
                    assertEquals(GitSynchronisationStatus.NONE, status, "Never indexed")
                    assertTrue(branches.isEmpty(), "No branch before the indexation")
                    assertNull(lastRunDate, "Never run")
                    assertNull(lastError, "No error")
                }

                index()

                gitService.getProjectGitSyncInfo(this).apply {
                    assertEquals(GitSynchronisationStatus.IDLE, status, "Indexed")
                    assertTrue("feature" in branches.map { it.name }, "Branches of the repository")
                    assertNotNull(lastRunDate, "Last run of the indexation")
                    assertNull(lastError, "No error")
                }
            }
        }
    }

    @Test
    fun `a reset bumps the generation of the repository and runs the indexation`() {
        withRepo { repo ->
            project {
                gitProject(repo, sync = false)
                asAdmin().execute { jobScheduler.refresh() }
                val repository = gitService.getProjectConfiguration(this)!!.gitRepository
                val generation = gitCloneGenerationService.getGeneration(repository)
                val runs = indexationRuns()

                assertTrue(gitService.projectSync(this, GitSynchronisationRequest(true)).success, "Reset")

                assertEquals(generation + 1, gitCloneGenerationService.getGeneration(repository), "Generation bumped")
                waitUntil("Indexation run", interval = 500.milliseconds, timeout = 30.seconds) {
                    indexationRuns() > runs
                }
            }
        }
    }

    @Test
    fun `a synchronisation without reset keeps the generation of the repository`() {
        withRepo { repo ->
            project {
                gitProject(repo, sync = false)
                asAdmin().execute { jobScheduler.refresh() }
                val repository = gitService.getProjectConfiguration(this)!!.gitRepository
                val generation = gitCloneGenerationService.getGeneration(repository)

                index()

                assertEquals(generation, gitCloneGenerationService.getGeneration(repository), "Same generation")
            }
        }
    }

    private fun net.nemerosa.ontrack.model.structure.Project.index() {
        val configuration = gitService.getProjectConfiguration(this)!!
        val run = gitService.sync(configuration, GitSynchronisationRequest.SYNC)
        assertNotNull(run, "Indexation launched").get(30, TimeUnit.SECONDS)
    }

    private fun net.nemerosa.ontrack.model.structure.Project.indexationRuns(): Long =
        jobScheduler.getJobStatus(
            GIT_JOB_CATEGORY.getType("git-indexation").getKey(gitService.getProjectConfiguration(this)!!.gitRepository.id)
        ).map { it.runCount }.orElse(0)
}
