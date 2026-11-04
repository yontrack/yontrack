package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mock SCM over GraphQL, which is how the demo seed reaches it: the demo's ingress routes
 * `/graphql` to the backend and everything else to the Next UI, so the REST endpoints in
 * [MockSCMController] answer 404 there whatever the instance is configured with.
 */
class MockSCMMutationsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var mockSCMExtension: MockSCMExtension

    @Test
    fun `Registering a commit`() {
        val repository = uid("repo-")
        asAdmin {
            val id = registerCommit(repository, "main", "feat: something")
            assertEquals(
                "feat: something",
                mockSCMExtension.repository(repository).getCommit(id)?.message,
            )
        }
    }

    @Test
    fun `Registering a commit with its author`() {
        val repository = uid("repo-")
        asAdmin {
            run(
                """
                    mutation {
                        mockScmRegisterCommit(input: {
                            repository: "$repository",
                            scmBranch: "main",
                            message: "feat: something",
                            author: "Maria Escobar",
                            authorEmail: "maria.escobar@example.com"
                        }) {
                            commit { author authorEmail }
                            errors { message }
                        }
                    }
                """
            ) { data ->
                assertNoUserError(data, "mockScmRegisterCommit")
                val commit = data.path("mockScmRegisterCommit").path("commit")
                assertEquals("Maria Escobar", commit.path("author").asText())
                assertEquals("maria.escobar@example.com", commit.path("authorEmail").asText())
            }
        }
    }

    @Test
    fun `Registering a commit without an author`() {
        val repository = uid("repo-")
        asAdmin {
            run(
                """
                    mutation {
                        mockScmRegisterCommit(input: {
                            repository: "$repository",
                            scmBranch: "main",
                            message: "feat: something"
                        }) {
                            commit { author authorEmail }
                            errors { message }
                        }
                    }
                """
            ) { data ->
                assertNoUserError(data, "mockScmRegisterCommit")
                val commit = data.path("mockScmRegisterCommit").path("commit")
                assertEquals("unknown", commit.path("author").asText())
                assertTrue(commit.path("authorEmail").isNull, "No email")
            }
        }
    }

    @Test
    fun `Registering an issue and linking it to a commit`() {
        val repository = uid("repo-")
        asAdmin {
            run(
                """
                    mutation {
                        mockScmRegisterIssue(input: {
                            repository: "$repository",
                            key: "ISS-21",
                            message: "Some new feature",
                            type: "feature"
                        }) { errors { message } }
                    }
                """
            ) { data ->
                assertNoUserError(data, "mockScmRegisterIssue")
            }

            val id = registerCommit(repository, "main", "feat: ISS-21 some new feature")

            // The mock SCM links a commit to an issue as the commit comes in, which is why the
            // issue has to be registered first.
            assertEquals(id, mockSCMExtension.repository(repository).findIssue("ISS-21")?.lastCommitId())
        }
    }

    /**
     * Commit ids come from the position of the commit on its branch, so a seed registering the
     * same commits on every run has to empty the repository first or every id changes.
     */
    @Test
    fun `Deleting a repository starts its commit numbering over`() {
        val repository = uid("repo-")
        asAdmin {
            val first = registerCommit(repository, "main", "feat: something")
            val second = registerCommit(repository, "main", "feat: something")
            assertNotEquals(first, second, "A second commit is a different commit")

            run(
                """
                    mutation {
                        mockScmDeleteRepository(input: { repository: "$repository" }) {
                            errors { message }
                        }
                    }
                """
            ) { data ->
                assertNoUserError(data, "mockScmDeleteRepository")
            }

            assertEquals(
                first,
                registerCommit(repository, "main", "feat: something"),
                "The repository starts over after being deleted",
            )
        }
    }

    /**
     * Unlike the REST endpoints, these mutations are reachable from outside the cluster on any
     * instance running the mock SCM — the demo included — so they ask for more than an
     * authenticated user.
     */
    @Test
    fun `Registering a commit is not granted to just any authenticated user`() {
        val repository = uid("repo-")
        asAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY) {
            // The GraphQL error surfaces through the test support's own assertion, so this
            // catches a Throwable rather than an Exception.
            val error = assertFailsWith<Throwable> {
                registerCommit(repository, "main", "feat: something")
            }
            assertTrue("GlobalSettings" in error.message.orEmpty(), error.message.orEmpty())
        }
        asAdmin {
            assertNull(mockSCMExtension.findRepository(repository), "Nothing was registered")
        }
    }

    private fun registerCommit(repository: String, scmBranch: String, message: String): String {
        var id: String? = null
        run(
            """
                mutation {
                    mockScmRegisterCommit(input: {
                        repository: "$repository",
                        scmBranch: "$scmBranch",
                        message: "$message"
                    }) {
                        commit { id }
                        errors { message }
                    }
                }
            """
        ) { data ->
            assertNoUserError(data, "mockScmRegisterCommit")
            id = data.path("mockScmRegisterCommit").path("commit").getRequiredTextField("id")
        }
        return id ?: error("No commit id returned")
    }

}
