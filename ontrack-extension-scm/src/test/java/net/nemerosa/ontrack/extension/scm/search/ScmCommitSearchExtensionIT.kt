package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Search documents for the SCM commits, on Postgres.
 *
 * Mock commit IDs only depend on the SCM branch and the position on it, so the same ID exists in
 * many repositories: the results are always narrowed to the project under test.
 */
@AsAdminTest
class ScmCommitSearchExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var scmCommitSearchExtension: ScmCommitSearchExtension

    @Autowired
    private lateinit var searchService: SearchService

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentService

    private fun search(query: String) =
        searchService.search(
            SearchQueryRequest(
                query = query,
                types = listOf(ScmCommitSearchExtension.SCM_COMMIT_SEARCH_RESULT_TYPE),
                size = 1000,
            )
        )

    private fun searchInProject(query: String, project: Project): List<SearchResult> =
        search(query).items.filter { it.projectName == project.name }

    /**
     * Whether a commit is found by its hash in a project. Similar hashes are found as well, by
     * trigram: only the commit itself counts.
     */
    private fun isFound(commit: String, project: Project): Boolean =
        asUser { searchInProject(commit, project) }.any { it.title == commit }

    @Suppress("UNCHECKED_CAST")
    private val SearchResult.item: Map<String, *> get() = data?.get(SearchResult.SEARCH_RESULT_ITEM) as Map<String, *>

    private val SearchResult.projectName: String? get() = item["projectName"] as String?

    private fun indexNewCommits(project: Project): Int =
        asAdmin { scmCommitSearchExtension.indexNewCommits(project) }

    /**
     * A branch configured for the mock SCM, on a SCM branch of its own
     */
    private fun MockSCMTester.MockSCMRepositoryContext.mockBranch(project: Project): Branch =
        project.branch {
            configureMockSCMBranch(scmBranch = uid("b-"))
        }

    @Test
    fun `A commit is found by its hash, with what its result renders`() {
        mockSCMTester.withMockSCMRepository {
            project {
                val branch = mockBranch(this)
                val commit = branch.build().withRepositoryCommit("Some fix")
                indexNewCommits(this)
                val result = asUser { searchInProject(commit, this) }.single()
                assertEquals(commit, result.title)
                assertEquals("Some fix", result.description)
                assertEquals(
                    mapOf(
                        "projectName" to name,
                        "id" to commit,
                        "shortId" to commit,
                        "author" to "unknown",
                    ),
                    result.item
                )
                @Suppress("UNCHECKED_CAST")
                val projectData = result.data?.get("project") as Map<String, *>
                assertEquals(id(), projectData["id"])
                assertEquals(name, projectData["name"])
            }
        }
    }

    @Test
    fun `A commit is found by the words of its message`() {
        val word = uid("w")
        mockSCMTester.withMockSCMRepository {
            project {
                val branch = mockBranch(this)
                val commit = branch.build().withRepositoryCommit("Fixing $word for good")
                indexNewCommits(this)
                assertEquals(listOf(commit), asUser { searchInProject(word, this).map { it.item["id"] } })
            }
        }
    }

    @Test
    fun `The message of a commit is truncated to 2 KB`() {
        val word = uid("w")
        mockSCMTester.withMockSCMRepository {
            project {
                val branch = mockBranch(this)
                // Multibyte characters around the limit
                val message = "é".repeat(1500) + " $word"
                val commit = branch.build().withRepositoryCommit(message)
                indexNewCommits(this)
                val result = asUser { searchInProject(commit, this) }.single()
                assertEquals("é".repeat(1024), result.description)
                assertTrue(result.description.toByteArray().size <= ScmCommitSearchExtension.MAX_TEXT_BYTES)
                // What was cut is not searchable
                assertEquals(0, asUser { searchInProject(word, this).size })
            }
        }
    }

    @Test
    fun `The incremental scan indexes only the commits after the last indexed one`() {
        mockSCMTester.withMockSCMRepository {
            project {
                val branch = mockBranch(this)
                val build = branch.build()
                val commit1 = build.withRepositoryCommit("Commit 1", property = false)
                val commit2 = build.withRepositoryCommit("Commit 2", property = false)
                assertEquals(2, indexNewCommits(this), "First scan: all the commits")
                assertEquals(0, indexNewCommits(this), "Nothing new")
                val commit3 = build.withRepositoryCommit("Commit 3", property = false)
                assertEquals(1, indexNewCommits(this), "Second scan: only the new commit")
                listOf(commit1, commit2, commit3).forEach { commit ->
                    assertTrue(isFound(commit, this), "$commit is searchable")
                }
            }
        }
    }

    @Test
    fun `A commit already indexed is left as it is by the incremental scan`() {
        mockSCMTester.withMockSCMRepository {
            project {
                val build = mockBranch(this).build()
                val commit = build.withRepositoryCommit("Commit 1", property = false)
                // A document for this commit exists already
                asAdmin {
                    searchDocumentService.index(
                        SearchDocument(
                            type = ScmCommitSearchExtension.SCM_COMMIT_SEARCH_RESULT_TYPE,
                            key = ScmCommitSearchExtension.documentKey(this, commit),
                            projectId = id(),
                            entity = null,
                            title = commit,
                            identifiers = listOf(commit),
                            text = "Existing",
                            data = mapOf("item" to mapOf("projectName" to name)).asJson(),
                        )
                    )
                }
                assertEquals(1, indexNewCommits(this))
                assertEquals("Existing", asUser { searchInProject(commit, this) }.single().description)
            }
        }
    }

    @Test
    fun `Same commit in two projects is found in both`() {
        val commits = mutableListOf<String>()
        val projects = mutableListOf<Project>()
        val scmBranch = uid("b-")
        repeat(2) {
            mockSCMTester.withMockSCMRepository {
                project {
                    projects += this
                    val branch = branch { configureMockSCMBranch(scmBranch = scmBranch) }
                    commits += branch.build().withRepositoryCommit("Commit 1")
                    indexNewCommits(this)
                }
            }
        }
        val commit = commits.distinct().single()
        val names = projects.map { it.name }.toSet()
        val found = asUser { search(commit).items }.filter { it.projectName in names }
        assertEquals(names, found.map { it.projectName }.toSet())
    }

    @Test
    fun `The commits of a project are visible to the users who can see the project only`() {
        mockSCMTester.withMockSCMRepository {
            project {
                val commit = mockBranch(this).build().withRepositoryCommit("Commit 1")
                indexNewCommits(this)
                withNoGrantViewToAll {
                    asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                        assertEquals(1, searchInProject(commit, this).size)
                    }
                    project().asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                        assertEquals(0, searchInProject(commit, this).size)
                    }
                }
            }
        }
    }

    @Test
    fun `Indexing with errors is not blocking`() {
        val repo2 = uid("repo-2-")
        lateinit var project1: Project
        lateinit var commit: String
        mockSCMTester.withMockSCMRepository {
            project {
                project1 = this
                commit = mockBranch(this).build().withRepositoryCommit("Commit 1")
            }
        }
        mockSCMTester.withMockSCMRepository(repo2) {
            project {
                mockBranch(this).build().withRepositoryCommit("Commit 1")
            }
        }
        // Invalidates repo 2 to force an error
        mockSCMTester.deleteRepository(repo2)

        asAdmin { scmCommitSearchExtension.indexNewCommits() }

        assertTrue(isFound(commit, project1))
    }

    /**
     * The full scan is the rebuild of the type, which runs in its own transactions: the data of
     * the test must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `The weekly full scan writes all the commits and deletes the stale ones`() {
        mockSCMTester.withMockSCMRepository {
            val project = project {}
            try {
                val build = mockBranch(project).build()
                val commit1 = build.withRepositoryCommit("Commit 1", property = false)
                val commit2 = build.withRepositoryCommit("Commit 2", property = false)
                assertEquals(2, indexNewCommits(project))
                // A commit whose document is lost (a failed write, say) is not seen again by the
                // incremental scan...
                asAdmin {
                    searchDocumentService.delete(
                        ScmCommitSearchExtension.SCM_COMMIT_SEARCH_RESULT_TYPE,
                        ScmCommitSearchExtension.documentKey(project, commit1),
                    )
                }
                assertEquals(0, indexNewCommits(project))
                assertFalse(isFound(commit1, project))
                // ... and a commit which does not exist any longer (a force push, say) stays
                val stale = uid("stale")
                asAdmin {
                    searchDocumentService.index(
                        SearchDocument(
                            type = ScmCommitSearchExtension.SCM_COMMIT_SEARCH_RESULT_TYPE,
                            key = ScmCommitSearchExtension.documentKey(project, stale),
                            projectId = project.id(),
                            entity = null,
                            title = stale,
                            identifiers = listOf(stale),
                            text = null,
                            data = mapOf("item" to mapOf("projectName" to project.name)).asJson(),
                        )
                    )
                }
                // The full scan repairs both
                searchService.reindex(ScmCommitSearchExtension.SCM_COMMIT_SEARCH_RESULT_TYPE)
                assertTrue(isFound(commit1, project))
                assertTrue(isFound(commit2, project))
                assertFalse(isFound(stale, project))
                // The incremental scan goes on after the last commit of the full scan
                val commit3 = build.withRepositoryCommit("Commit 3", property = false)
                assertEquals(1, indexNewCommits(project))
                assertTrue(isFound(commit3, project))
            } finally {
                asAdmin { structureService.deleteProject(project.id) }
            }
        }
    }

    @Test
    fun `The full scan is scheduled every week`() {
        assertEquals(
            net.nemerosa.ontrack.job.Schedule.EVERY_WEEK,
            scmCommitSearchExtension.indexerSchedule
        )
    }

}
