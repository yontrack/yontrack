package net.nemerosa.ontrack.extension.scm.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.GetRequest
import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitProperty
import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.SearchIndexService
import net.nemerosa.ontrack.model.structure.SearchRequest
import net.nemerosa.ontrack.model.structure.SearchResult
import net.nemerosa.ontrack.model.structure.SearchService
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
@TestPropertySource(
    properties = [
        "ontrack.config.search.index.immediate=true",
        "ontrack.config.search.index.logging=true",
        "ontrack.config.search.index.tracing=true",
    ]
)
class ScmIssueSearchExtensionIT: AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var scmCommitSearchExtension: ScmCommitSearchExtension

    @Autowired
    private lateinit var scmIssueSearchExtension: ScmIssueSearchExtension

    @Autowired
    private lateinit var elasticsearchClient: ElasticsearchClient

    @Autowired
    private lateinit var searchIndexService: SearchIndexService

    @Autowired
    private lateinit var searchService: SearchService

    @Test
    fun `Searching issues`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()
                    build {

                        val issueKey = "ISS-1234"
                        repositoryIssue(key = issueKey, message = "Sample issue")
                        val commit = withRepositoryCommit("$issueKey Commit 1")
                        setProperty(
                            this,
                            MockSCMBuildCommitPropertyType::class.java,
                            MockSCMBuildCommitProperty(commit)
                        )
                        searchIndexService.index(scmCommitSearchExtension) // This includes the indexation of issues!

                        val results = searchService.paginatedSearch(
                            SearchRequest(
                                token = issueKey,
                                type = ScmIssueSearchExtension.SCM_ISSUE_SEARCH_RESULT_TYPE,
                            )
                        )

                        assertEquals(1, results.items.size, "One result expected")
                        val item = results.items.single().data?.get(SearchResult.SEARCH_RESULT_ITEM)
                        assertNotNull(item, "Result found") {
                            assertIs<ScmIssueSearchItem>(it) { si ->
                                assertEquals(issueKey, si.displayKey)
                            }
                        }

                    }
                }
            }
        }
    }

    @Test
    fun `Searching issues with a hash as a prefix`() {
        mockSCMTester.withIssuePattern("(#(\\d+))") {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()
                        build {

                            val issueKey = "#1234"
                            repositoryIssue(key = issueKey, message = "Sample issue")
                            val commit = withRepositoryCommit("$issueKey Commit 1")
                            setProperty(
                                this,
                                MockSCMBuildCommitPropertyType::class.java,
                                MockSCMBuildCommitProperty(commit)
                            )
                            searchIndexService.index(scmCommitSearchExtension) // This includes the indexation of issues!

                            val results = searchService.paginatedSearch(
                                SearchRequest(
                                    token = issueKey,
                                    type = ScmIssueSearchExtension.SCM_ISSUE_SEARCH_RESULT_TYPE,
                                )
                            )

                            assertEquals(1, results.items.size, "One result expected")
                            val item = results.items.single().data?.get(SearchResult.SEARCH_RESULT_ITEM)
                            assertNotNull(item, "Result found") {
                                assertIs<ScmIssueSearchItem>(it) { si ->
                                    assertEquals(issueKey, si.displayKey)
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Issue keys no longer extracted are removed from the index of the project`() {
        // Another project, which keeps its issues
        val other = project()
        searchIndexService.createSearchIndex(
            scmIssueSearchExtension,
            ScmIssueSearchItem(other.name, "ISS-2", "ISS-2")
        )
        mockSCMTester.withMockSCMRepository {
            project {
                // An issue which used to be extracted from the body of a commit
                searchIndexService.createSearchIndex(
                    scmIssueSearchExtension,
                    ScmIssueSearchItem(name, "ISS-2", "ISS-2")
                )
                branch {
                    configureMockSCMBranch()
                    build {
                        repositoryIssue(key = "ISS-1", message = "Sample issue")
                        repositoryIssue(key = "ISS-2", message = "Mentioned issue")
                        withRepositoryCommit("ISS-1 Commit 1\n\nFollow-up of ISS-2, mentioned in the body only.")

                        searchIndexService.index(scmCommitSearchExtension) // This includes the indexation of issues!

                        fun projectsOf(issueKey: String) = searchService.paginatedSearch(
                            SearchRequest(
                                token = issueKey,
                                type = ScmIssueSearchExtension.SCM_ISSUE_SEARCH_RESULT_TYPE,
                                size = 1000,
                            )
                        ).items.mapNotNull { result ->
                            (result.data?.get(SearchResult.SEARCH_RESULT_ITEM) as? ScmIssueSearchItem)
                                ?.takeIf { it.key == issueKey }
                                ?.projectName
                        }.toSet()

                        assertTrue(project.name in projectsOf("ISS-1"), "Issue in the subject is indexed")
                        assertFalse(project.name in projectsOf("ISS-2"), "Issue mentioned in the body only is removed")
                        // The other project has no SCM, so its issues are not returned by a search
                        assertTrue(
                            elasticsearchClient.get(
                                GetRequest.of { it.index(scmIssueSearchExtension.indexName).id("${other.name}::ISS-2") },
                                Any::class.java
                            ).found(),
                            "Issues of other projects are kept"
                        )
                    }
                }
            }
        }
    }

}
