package net.nemerosa.ontrack.extension.git.property

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.model.structure.BuildSearchForm
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitCommitPropertyTypeIT : AbstractGitTestSupport() {

    @Test
    fun `Build filter on commit`() {
        createRepo {
            commits(5)
        } and { repo, commits ->
            project {
                gitProject(repo)
                branch {
                    gitBranch {
                        commitAsProperty()
                    }
                    (1..5).forEach { index ->
                        build(index.toString()) {
                            gitCommitProperty(commits.getValue(index))
                        }
                    }
                    // Looks for commit n°3
                    val builds = buildFilterService.standardFilterProviderData(1)
                        .withWithProperty(GitCommitPropertyType::class.java.name)
                        .withWithPropertyValue(commits.getValue(3))
                        .build()
                        .filterBranchBuilds(this)
                    assertEquals(
                        listOf("3"),
                        builds.map { it.name }
                    )
                }
            }
        }
    }

    @Test
    fun `Build project search on commit`() {
        createRepo {
            commits(5)
        } and { repo, commits ->
            project {
                gitProject(repo)
                branch {
                    gitBranch {
                        commitAsProperty()
                    }
                    (1..5).forEach { index ->
                        build(index.toString()) {
                            gitCommitProperty(commits.getValue(index))
                        }
                    }
                    // Looks for commit n°3 at project level
                    val form = BuildSearchForm(
                        property = GitCommitPropertyType::class.java.name,
                        propertyValue = commits.getValue(3),
                    )
                    val builds = structureService.buildSearch(project.id, form)
                    assertEquals(
                        listOf("3"),
                        builds.map { it.name }
                    )
                }
            }
        }
    }

    @Test
    fun `Build branch search on commit`() {
        createRepo {
            commits(5)
        } and { repo, commits ->
            project {
                gitProject(repo)
                branch {
                    gitBranch {
                        commitAsProperty()
                    }
                    val builds = (1..5).map { index ->
                        build(index.toString()) {
                            gitCommitProperty(commits.getValue(index))
                        }
                    }
                    // Looks for commit n°3 at branch level
                    val build = propertyService.findBuildByBranchAndSearchkey(id, GitCommitPropertyType::class.java, commits.getValue(3))
                    assertEquals(
                        builds[2].id,
                        build,
                        "Found build by commit"
                    )
                }
            }
        }
    }
}
