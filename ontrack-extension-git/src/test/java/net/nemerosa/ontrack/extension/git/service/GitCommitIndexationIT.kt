package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationPropertyType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GitCommitIndexationIT : AbstractGitTestSupport() {

    @Test
    fun `Indexation job catching up with missing commits`() {
        createRepo {
            commits(10, pauses = false)
        } and { repo, commits ->
            project {
                gitProject(repo)
                branch {
                    gitBranch {
                        commitAsProperty()
                    }
                    // Creates 10 builds with a commit property (but for n°6)
                    commits.forEach { no, commit ->
                        build(name = "$no") {
                            if (no != 6) {
                                gitCommitProperty(commit)
                            }
                        }
                    }
                    // Checks that NO indexed Git commit is stored for this build
                    val build = structureService.findBuildByName(project.name, name, "6").orElse(null)!!
                    var commitForBuild = gitService.getCommitForBuild(build)
                    assertNull(commitForBuild, "No commit stored for build")

                    // Before setting the Git commit property for the build
                    // we have to simulate the fact that the indexation won't work
                    val projectProperty = asAdmin().call {
                        val property = propertyService.getProperty(project, LocalGitProjectConfigurationPropertyType::class.java).value
                        propertyService.deleteProperty(project, LocalGitProjectConfigurationPropertyType::class.java)
                        property
                    }!!

                    // Setting the property
                    build.gitCommitProperty(commits.getValue(6))

                    // Checks it's not set
                    commitForBuild = gitService.getCommitForBuild(build)
                    assertNull(commitForBuild, "No commit stored for build yet")

                    // Resets the project configuration
                    asAdmin().execute {
                        propertyService.editProperty(
                                project,
                                LocalGitProjectConfigurationPropertyType::class.java,
                                projectProperty
                        )
                    }

                    // Indexation of the branch in an incremental way
                    gitService.collectIndexableGitCommitForBranch(this, overrides = false)

                    // Checks the commit is set
                    commitForBuild = gitService.getCommitForBuild(build)
                    assertNotNull(commitForBuild, "Commit is now stored for build") {
                        assertEquals(
                                commits[6],
                                it.commit.id
                        )
                    }

                }
            }
        }
    }

}