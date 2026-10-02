package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.model.structure.SearchDocumentService
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.SearchResult
import net.nemerosa.ontrack.model.structure.SearchService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Search documents for the issues found in the commit messages, on Postgres. They are written by
 * the scan of the commits.
 */
@AsAdminTest
class ScmIssueSearchExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var scmCommitSearchExtension: ScmCommitSearchExtension

    @Autowired
    private lateinit var searchService: SearchService

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentService

    private fun searchInProject(query: String, project: Project): List<SearchResult> =
        searchService.search(
            SearchQueryRequest(
                query = query,
                types = listOf(ScmIssueSearchExtension.SCM_ISSUE_SEARCH_RESULT_TYPE),
                size = 1000,
            )
        ).items.filter { it.item["projectName"] == project.name }

    @Suppress("UNCHECKED_CAST")
    private val SearchResult.item: Map<String, *> get() = data?.get(SearchResult.SEARCH_RESULT_ITEM) as Map<String, *>

    private fun issueKey() = "ISS-" + uid("").filter { it.isDigit() }

    @Test
    fun `Issues are indexed by the scan of the commits, with what their result renders`() {
        val issueKey = issueKey()
        mockSCMTester.withMockSCMRepository {
            project {
                val branch = branch { configureMockSCMBranch() }
                repositoryIssue(key = issueKey, message = "Sample issue")
                branch.build().withRepositoryCommit("$issueKey Commit 1")
                asAdmin { scmCommitSearchExtension.indexNewCommits(this) }

                val result = asUser { searchInProject(issueKey, this) }.single()
                assertEquals(issueKey, result.title)
                assertEquals(
                    mapOf(
                        "projectName" to name,
                        "key" to issueKey,
                        "displayKey" to issueKey,
                    ),
                    result.item
                )
                @Suppress("UNCHECKED_CAST")
                assertEquals(id(), (result.data?.get("project") as Map<String, *>)["id"])
            }
        }
    }

    @Test
    fun `Issues with a hash as a prefix`() {
        mockSCMTester.withIssuePattern("(#(\\d+))") {
            mockSCMTester.withMockSCMRepository {
                project {
                    val branch = branch { configureMockSCMBranch() }
                    val issueKey = "#1234"
                    repositoryIssue(key = issueKey, message = "Sample issue")
                    branch.build().withRepositoryCommit("$issueKey Commit 1")
                    asAdmin { scmCommitSearchExtension.indexNewCommits(this) }

                    val result = asUser { searchInProject(issueKey, this) }.single()
                    assertEquals(issueKey, result.item["displayKey"])
                }
            }
        }
    }

    @Test
    fun `Issues of new commits are indexed by the incremental scan`() {
        val issue1 = issueKey()
        val issue2 = issueKey()
        mockSCMTester.withMockSCMRepository {
            project {
                val build = branch { configureMockSCMBranch() }.build()
                repositoryIssue(key = issue1, message = "Issue 1")
                repositoryIssue(key = issue2, message = "Issue 2")
                build.withRepositoryCommit("$issue1 Commit 1", property = false)
                asAdmin { scmCommitSearchExtension.indexNewCommits(this) }
                build.withRepositoryCommit("$issue2 Commit 2", property = false)
                asAdmin { scmCommitSearchExtension.indexNewCommits(this) }

                // Similar keys match too, by trigram, after the exact one
                assertEquals(issue1, asUser { searchInProject(issue1, this) }.first().item["key"])
                assertEquals(issue2, asUser { searchInProject(issue2, this) }.first().item["key"])
            }
        }
    }

    /**
     * The rebuild runs in its own transactions: the data of the test must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the issues scans the commits again`() {
        val issueKey = issueKey()
        mockSCMTester.withMockSCMRepository {
            val project = project {}
            try {
                val build = project.branch { configureMockSCMBranch() }.build()
                repositoryIssue(key = issueKey, message = "Sample issue")
                build.withRepositoryCommit("$issueKey Commit 1")
                searchService.reindex(ScmIssueSearchExtension.SCM_ISSUE_SEARCH_RESULT_TYPE)
                assertEquals(1, asUser { searchInProject(issueKey, project).size })
            } finally {
                asAdmin { structureService.deleteProject(project.id) }
            }
        }
    }

    @Test
    fun `Issue keys no longer named by the commits are removed by a full scan of the project`() {
        val subjectIssue = issueKey()
        val bodyIssue = issueKey()
        // Another project, which keeps its issues
        val other = project()
        indexIssue(other, bodyIssue)
        mockSCMTester.withMockSCMRepository {
            project {
                // An issue which used to be extracted from the body of a commit
                indexIssue(this, bodyIssue)
                val build = branch { configureMockSCMBranch() }.build()
                repositoryIssue(key = subjectIssue, message = "Sample issue")
                repositoryIssue(key = bodyIssue, message = "Mentioned issue")
                build.withRepositoryCommit("$subjectIssue Commit 1\n\nFollow-up of $bodyIssue, mentioned in the body only.")
                // The first scan of a project is a full one
                asAdmin { scmCommitSearchExtension.indexNewCommits(this) }

                fun keysIn(project: Project, key: String) =
                    asAdmin { searchInProject(key, project) }.map { it.item["key"] }

                assertTrue(subjectIssue in keysIn(this, subjectIssue), "Issue in the subject is indexed")
                assertFalse(bodyIssue in keysIn(this, bodyIssue), "Issue mentioned in the body only is removed")
                assertTrue(bodyIssue in keysIn(other, bodyIssue), "Issues of other projects are kept")
            }
        }
    }

    private fun indexIssue(project: Project, key: String) {
        searchDocumentService.index(
            SearchDocument(
                type = ScmIssueSearchExtension.SCM_ISSUE_SEARCH_RESULT_TYPE,
                key = ScmIssueSearchExtension.documentKey(project, key),
                projectId = project.id(),
                entity = null,
                title = key,
                identifiers = listOf(key),
                text = null,
                data = mapOf(
                    SearchResult.SEARCH_RESULT_ITEM to mapOf(
                        "projectName" to project.name,
                        "key" to key,
                        "displayKey" to key,
                    ),
                ).asJson(),
            )
        )
    }

}
