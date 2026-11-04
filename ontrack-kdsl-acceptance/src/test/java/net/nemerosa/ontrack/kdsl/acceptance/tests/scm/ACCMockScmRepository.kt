package net.nemerosa.ontrack.kdsl.acceptance.tests.scm

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.extension.scm.MockScmRepositoryContext.MockSCMCommitAuthor
import net.nemerosa.ontrack.kdsl.spec.extension.scm.withMockScmRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ACCMockScmRepository : AbstractACCDSLTestSupport() {

    @Test
    fun `a commit is read back with the author it was registered with`() {
        withMockScmRepository(ontrack, prefix = "acc-mock-scm") {
            project {
                configuredForMockScm()

                val withAuthor = repositoryCommit(
                    "feat: something",
                    author = "Maria Escobar",
                    authorEmail = "maria.escobar@example.com",
                )
                val withoutAuthor = repositoryCommit("feat: something else")

                assertEquals(
                    MockSCMCommitAuthor("Maria Escobar", "maria.escobar@example.com"),
                    commitAuthor(withAuthor),
                )
                assertEquals(MockSCMCommitAuthor("unknown", null), commitAuthor(withoutAuthor))
            }
        }
    }

    /**
     * The mock SCM keeps its repositories on the server, where they outlive the entities
     * pointing at them, and it derives a commit id from the position of the commit on its
     * branch. Anything registering the same commits twice — the demo seed does, on every
     * reset — therefore has to empty the repository first, or every id changes.
     */
    @Test
    fun `deleting a repository starts its commit numbering over`() {
        withMockScmRepository(ontrack, prefix = "acc-mock-scm") {
            val first = repositoryCommit("feat: something")
            val second = repositoryCommit("feat: something")
            assertNotEquals(first, second, "A second commit is a different commit")

            deleteRepository()

            assertEquals(
                first,
                repositoryCommit("feat: something"),
                "The repository starts over after being deleted",
            )
        }
    }
}
