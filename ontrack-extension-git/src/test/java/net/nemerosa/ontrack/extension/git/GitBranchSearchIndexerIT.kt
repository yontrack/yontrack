package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.model.structure.SearchRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

/**
 * Testing the search on Git branches.
 */
@Disabled("flaky")
class GitBranchSearchIndexerIT : AbstractGitSearchTestSupport() {

    @Autowired
    protected lateinit var gitBranchSearchIndexer: GitBranchSearchIndexer

    @BeforeEach
    fun before() {
        searchIndexService.resetIndex(gitBranchSearchIndexer, reindex = false, logErrors = false)
    }

    @Test
    fun `Looking for a Git branch`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                gitProject(repo)
                val branch = branch {
                    gitBranch("release/1.0")
                    // Re-indexes the commits
                    searchIndexService.index(gitBranchSearchIndexer)
                }
                // Looks for the branch
                val results = searchService.paginatedSearch(SearchRequest("release/1.0", gitBranchSearchIndexer.searchResultType.id)).items
                assertTrue(results.any { it.title == branch.entityDisplayName }, "Branch found from Git branch")
            }
        }
    }

    @Test
    fun `Looking for a Git branch after the Ontrack branch is deleted`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                gitProject(repo)
                val branch = branch {
                    gitBranch("release/1.0")
                    // Re-indexes the commits
                    searchIndexService.index(gitBranchSearchIndexer)
                }
                // Looks for the branch
                val results = searchService.paginatedSearch(SearchRequest("release/1.0", gitBranchSearchIndexer.searchResultType.id)).items
                assertTrue(results.any { it.title == branch.entityDisplayName }, "Branch found from Git branch")
                // Deletes the branch
                branch.delete()
                // Looks for the branch again
                val newResults = searchService.paginatedSearch(SearchRequest("release/1.0", gitBranchSearchIndexer.searchResultType.id)).items
                assertTrue(newResults.none { it.title == branch.entityDisplayName }, "Branch not found from Git branch")
            }
        }
    }

    @Test
    fun `Looking for a Git branch just after it has been assigned`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                gitProject(repo)
                val branch = branch {
                    gitBranch("release/1.0")
                }
                // Looks for the branch
                val results = searchService.paginatedSearch(SearchRequest("release/1.0", gitBranchSearchIndexer.searchResultType.id)).items
                assertTrue(results.any { it.title == branch.entityDisplayName }, "Branch found from Git branch")
            }
        }
    }

    @Test
    fun `Looking for a Git branch just after it has been unassigned`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            project {
                gitProject(repo)
                val branch = branch {
                    gitBranch("release/1.0")
                }
                // Looks for the branch
                val results = searchService.paginatedSearch(SearchRequest("release/1.0", gitBranchSearchIndexer.searchResultType.id)).items
                assertTrue(results.any { it.title == branch.entityDisplayName }, "Branch found from Git branch")
                // Now, removes the Git branch
                deleteProperty(branch, GitBranchConfigurationPropertyType::class.java)
                // Looks for the branch again
                val newResults = searchService.paginatedSearch(SearchRequest("release/1.0", gitBranchSearchIndexer.searchResultType.id)).items
                assertTrue(newResults.none { it.title == branch.entityDisplayName }, "Branch not found from Git branch")
            }
        }
    }


}
