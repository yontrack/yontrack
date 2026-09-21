package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitProperty
import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.SearchIndexService
import net.nemerosa.ontrack.model.structure.SearchRequest
import net.nemerosa.ontrack.model.structure.SearchResult
import net.nemerosa.ontrack.model.structure.SearchService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@AsAdminTest
@TestPropertySource(
    properties = [
        "ontrack.config.search.index.immediate=true",
        "ontrack.config.search.index.logging=true",
        "ontrack.config.search.index.tracing=true",
    ]
)
class ScmCommitSearchExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var scmCommitSearchExtension: ScmCommitSearchExtension

    @Autowired
    private lateinit var searchIndexService: SearchIndexService

    @Autowired
    private lateinit var searchService: SearchService

    @Test
    fun `Searching commits`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()
                    build {

                        val commit = withRepositoryCommit("Commit 1")
                        setProperty(
                            this,
                            MockSCMBuildCommitPropertyType::class.java,
                            MockSCMBuildCommitProperty(commit)
                        )
                        searchIndexService.index(scmCommitSearchExtension)

                        val item = searchCommit(commit).single { it.projectName == project.name }
                        assertEquals(commit, item.id)

                    }
                }
            }
        }
    }

    @Test
    fun `Same commit in two projects is found in both`() {
        // Mock commit IDs only depend on the branch and the position on it, so the first commit
        // of two repositories has the same ID - like a commit shared by a fork and its origin
        val commits = mutableListOf<String>()
        val projectNames = mutableListOf<String>()
        repeat(2) {
            mockSCMTester.withMockSCMRepository(uid("repo-")) {
                project {
                    projectNames += name
                    branch {
                        configureMockSCMBranch()
                        build {
                            val commit = withRepositoryCommit("Commit 1")
                            commits += commit
                            setProperty(
                                this,
                                MockSCMBuildCommitPropertyType::class.java,
                                MockSCMBuildCommitProperty(commit)
                            )
                        }
                    }
                }
            }
        }
        val commit = commits.distinct().single()

        searchIndexService.index(scmCommitSearchExtension)

        val items = searchCommit(commit).filter { it.projectName in projectNames }
        assertEquals(projectNames.toSet(), items.map { it.projectName }.toSet())
        items.forEach { assertEquals(commit, it.id) }
    }

    @Test
    fun `Indexing with errors are not blocking`() {
        val repo1 = uid("repo-1-")
        val repo2 = uid("repo-2-")

        var commit = ""
        var projectName = ""

        mockSCMTester.withMockSCMRepository(repo1) {
            project {
                projectName = name
                branch {
                    configureMockSCMBranch()
                    build {
                        commit = withRepositoryCommit("Commit 1")
                        setProperty(
                            this,
                            MockSCMBuildCommitPropertyType::class.java,
                            MockSCMBuildCommitProperty(commit)
                        )
                    }
                }
            }
        }

        mockSCMTester.withMockSCMRepository(repo2) {
            project {
                branch {
                    configureMockSCMBranch()
                    build {
                        val commit = withRepositoryCommit("Commit 1")
                        setProperty(
                            this,
                            MockSCMBuildCommitPropertyType::class.java,
                            MockSCMBuildCommitProperty(commit)
                        )
                    }
                }
            }
        }

        // Invalidates repo 2 to force an error
        mockSCMTester.deleteRepository(repo2)

        searchIndexService.index(scmCommitSearchExtension)

        // No exception is expected, but repo1 was correctly indexed

        assertTrue(commit.isNotBlank(), "Commit has been indexed")

        val item = searchCommit(commit).single { it.projectName == projectName }
        assertEquals(commit, item.id)
    }

    /**
     * Searches for a commit ID. The index is shared by all the tests and mock commit IDs are
     * the same in every repository, so the results must be narrowed to the project under test.
     */
    private fun searchCommit(commit: String): List<ScmCommitSearchItem> =
        searchService.paginatedSearch(
            SearchRequest(
                token = commit,
                type = ScmCommitSearchExtension.SCM_COMMIT_SEARCH_RESULT_TYPE,
                size = 1000,
            )
        ).items.mapNotNull {
            it.data?.get(SearchResult.SEARCH_RESULT_ITEM) as? ScmCommitSearchItem
        }

}