package net.nemerosa.ontrack.extension.git.service


import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationProperty
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.model.ConfiguredBuildGitCommitLink
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.support.TagBuildNameGitCommitLink
import net.nemerosa.ontrack.extension.git.support.TagPatternBuildNameGitCommitLink
import net.nemerosa.ontrack.extension.scm.support.TagPattern
import net.nemerosa.ontrack.git.support.GitRepo
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.support.NoConfig
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    private lateinit var tagBuildNameGitCommitLink: TagBuildNameGitCommitLink
    @Autowired
    private lateinit var jobScheduler: JobScheduler

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
            asUser().withProjectFunction(project, ProjectEdit::class.java).call {
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
                    jobScheduler.refresh()
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

    /**
     * The build synchronisation run as a job, on a thread of the scheduler, whose checks of the
     * branch need the rights of the job (#2085). Committed, for the job to see the branch.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `sync launched as a job`() {
        GitRepo.prepare {
            gitInit()
            commit(1)
            tag("1.0.0")
        } and { _, repo ->
            val branch = doCreateBranch()
            val project = branch.project
            asAdmin().execute {
                propertyService.editProperty(
                    project,
                    LocalGitProjectConfigurationPropertyType::class.java,
                    LocalGitProjectConfigurationProperty(
                        name = uid("C"),
                        remote = "file://${repo.dir.absolutePath}",
                        issueServiceConfigurationIdentifier = null,
                    )
                )
                propertyService.editProperty(
                    branch,
                    GitBranchConfigurationPropertyType::class.java,
                    GitBranchConfigurationProperty(
                        "main",
                        ConfiguredBuildGitCommitLink(tagBuildNameGitCommitLink, NoConfig.INSTANCE).toServiceConfiguration(),
                        false, 7 * 24 * 60
                    )
                )
                jobScheduler.refresh()
            }

            val run = gitService.launchBuildSync(branch.id, false)
            assertNotNull(run, "Build synchronisation launched").get(30, TimeUnit.SECONDS)

            assertTrue(structureService.findBuildByName(project.name, branch.name, "1.0.0").isPresent, "Build created from its tag")
        }
    }

}
