package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationProperty
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.issues.mock.TestIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.model.toIdentifier
import net.nemerosa.ontrack.git.support.GitRepo
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.job.orchestrator.JobOrchestrator
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@AsAdminTest
class GitIndexationJobIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var jobOrchestrator: JobOrchestrator

    /**
     * Regression test for #434. Checks that changing the Git configuration of a project does change its
     * indexation job.
     */
    @Test
    fun `Git configuration change changes the indexation job`() {
        GitRepo.prepare {

            // Some content
            gitInit()
            commit(1, "#1")
            commit(2, "#2")
            log()

            // Configuration of the local Git repository
            val gitConfigurationName = uid("C")
            val gitConfiguration = LocalGitProjectConfigurationProperty(
                    name = gitConfigurationName,
                    remote = "file://${dir.absolutePath}",
                    issueServiceConfigurationIdentifier = TestIssueServiceConfiguration.INSTANCE.toIdentifier().format(),
            )

            // Creates a project
            val project = doCreateProject()

            // Configures the project
            asUser().withProjectFunction(project, ProjectEdit::class.java).call {
                propertyService.editProperty(
                        project,
                        LocalGitProjectConfigurationPropertyType::class.java,
                        gitConfiguration
                )
            }

            // Runs the orchestration
            asAdmin().execute {
                jobOrchestrator.orchestrate(JobRunListener.out())
            }

            // Checks that the indexation job is registered
            var statuses = jobScheduler.jobStatuses
            var status = statuses.find {
                it.description == "file://${dir.absolutePath} ($gitConfigurationName @ local)"
            }
            assertNotNull(status, "The indexation job must be present")

            // Creates a new repository all together
            val newRepo = GitRepo()
            newRepo.apply {
                gitInit()
                commit(1, "#1")
                commit(2, "#2")
                log()
            }

            // Updates the configuration
            asUser().withProjectFunction(project, ProjectEdit::class.java).call {
                propertyService.editProperty(
                        project,
                        LocalGitProjectConfigurationPropertyType::class.java,
                        gitConfiguration.copy(remote = "file://${newRepo.dir.absolutePath}")
                )
            }

            // Runs the orchestration
            asAdmin().execute {
                jobOrchestrator.orchestrate(JobRunListener.out())
            }

            // Checks that the NEW indexation job is registered
            statuses = jobScheduler.jobStatuses
            status = statuses.find {
                it.description == "file://${newRepo.dir.absolutePath} (${gitConfigurationName} @ local)"
            }
            assertNotNull(status, "The new indexation job must be present")
            // Checks that the OLD indexation job is gone
            status = statuses.find {
                it.description == "file://${dir.absolutePath} ($gitConfigurationName @ local)"
            }
            assertNull(status, "The old indexation job must be done")
        }
    }

}
