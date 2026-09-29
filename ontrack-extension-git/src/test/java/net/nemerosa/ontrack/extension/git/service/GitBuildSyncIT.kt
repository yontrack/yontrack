package net.nemerosa.ontrack.extension.git.service


import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationProperty
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.model.ConfiguredBuildGitCommitLink
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.support.TagPatternBuildNameGitCommitLink
import net.nemerosa.ontrack.extension.scm.support.TagPattern
import net.nemerosa.ontrack.git.support.GitRepo
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.job.orchestrator.JobOrchestrator
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testing the sync between builds and Git tags.
 */
@AsAdminTest
class GitBuildSyncIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var gitService: GitService
    @Autowired
    private lateinit var tagPatternBuildNameGitCommitLink: TagPatternBuildNameGitCommitLink
    @Autowired
    private lateinit var jobOrchestrator: JobOrchestrator

    @Test
    fun `main sync`() {
        // Git repo
        GitRepo.prepare {

            var no = 0
            gitInit()

            commit(no++)
            tag("1.1.6")
            commit(no++)
            tag("1.1.7")
            commit(no++)
            tag("1.2.0")
            commit(no++)
            tag("1.2.1")
            commit(no)
            tag("1.2.2")

            log()

        } and { _, repo ->

            // Configuration of the local Git repository
            val gitConfiguration = LocalGitProjectConfigurationProperty(
                    name = uid("C"),
                    remote = "file://${repo.dir.absolutePath}",
                    issueServiceConfigurationIdentifier = null,
            )

            // Creates a project and branch
            val branch = doCreateBranch()
            val project = branch.project

            // Configures the project and the branch
            asUser().with(project, ProjectEdit::class.java).call {
                propertyService.editProperty(
                        project,
                        LocalGitProjectConfigurationPropertyType::class.java,
                        gitConfiguration
                )
                // ...  & the branch with a link based on commits
                propertyService.editProperty(
                        branch,
                        GitBranchConfigurationPropertyType::class.java,
                        GitBranchConfigurationProperty(
                                "main",
                                ConfiguredBuildGitCommitLink(
                                        tagPatternBuildNameGitCommitLink,
                                        TagPattern("1.2.*")
                                ).toServiceConfiguration(),
                                false, 1
                        )
                )

                // Job registration
                asAdmin().execute {
                    jobOrchestrator.orchestrate(JobRunListener.out())
                }

                // Build synchronisation
                gitService.launchBuildSync(branch.id, true)

                // Checks the builds have been created
                assertFalse(structureService.findBuildByName(project.name, branch.name, "1.1.6").isPresent)
                assertFalse(structureService.findBuildByName(project.name, branch.name, "1.1.7").isPresent)
                assertTrue(structureService.findBuildByName(project.name, branch.name, "1.2.0").isPresent)
                assertTrue(structureService.findBuildByName(project.name, branch.name, "1.2.1").isPresent)
                assertTrue(structureService.findBuildByName(project.name, branch.name, "1.2.2").isPresent)
            }
        }
    }

}
