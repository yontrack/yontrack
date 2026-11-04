package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.scm.mock.MockSCMExtension.MockRepository
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseInto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testing what a [MockRepository] hands a [MockSCMStore], the repository restored from it, and
 * when the store is told a change happened.
 */
class MockRepositoryPersistenceTest {

    @Test
    fun `A repository restored from its snapshot holds the same thing`() {
        val original = populated()

        val restored = roundTrip(original)

        assertEquals(original.toData(), restored.toData())
    }

    @Test
    fun `A restored repository serves its commits, issues, files and pull requests`() {
        val restored = roundTrip(populated())

        val branch = assertNotNull(restored.getBranch("main"), "Branch is restored")
        assertEquals(
            listOf("ISS-1 First commit", "ISS-1 Second commit", "ISS-2 Third commit"),
            branch.commits.map { it.message },
        )

        val issue = assertNotNull(restored.findIssue("ISS-1"), "Issue is restored")
        assertEquals("Some feature", issue.message)
        assertEquals(setOf("feature"), issue.types)
        assertEquals(2, issue.commits.size, "Commits linked to the issue are restored")
        assertEquals(branch.commits[1].id, issue.lastCommitId())

        assertEquals("Some content", restored.getFile("main", "some/file.txt"))
        assertEquals(listOf("main"), restored.getBranchesForCommit(branch.commits[0].id))

        val pr = assertNotNull(restored.getPullRequestByName("#1"), "Pull request is restored")
        assertEquals("#1", pr.name)
        assertNotNull(restored.findPR(from = "feature/*", to = "main"), "Pull request is searchable")

        assertNotNull(restored.getBranch("feature/1"), "Branch created through createBranch is restored")
        assertNull(restored.getBranch("feature/2"), "Deleted branch is not restored")
    }

    @Test
    fun `A commit keeps its author and their email`() {
        val repository = MockRepository("petclinic")
        val id = repository.registerCommit(
            "main",
            "feat: something",
            author = "Maria Escobar",
            authorEmail = "maria.escobar@example.com",
        )

        val commit = assertNotNull(repository.getCommit(id))
        assertEquals("Maria Escobar", commit.author)
        assertEquals("maria.escobar@example.com", commit.authorEmail)
    }

    @Test
    fun `A commit with no author, or a blank one, is by nobody known`() {
        val repository = MockRepository("petclinic")
        listOf(
            repository.registerCommit("main", "feat: nothing given"),
            repository.registerCommit("main", "feat: blank given", author = " ", authorEmail = ""),
        ).forEach { id ->
            val commit = assertNotNull(repository.getCommit(id))
            assertEquals("unknown", commit.author)
            assertNull(commit.authorEmail)
        }
    }

    /**
     * What a persistent mock SCM would silently lose on a restart otherwise, going back to
     * `unknown` until the next reset of the demo.
     */
    @Test
    fun `A restored repository keeps the authors of its commits`() {
        val repository = MockRepository("petclinic")
        val id = repository.registerCommit(
            "main",
            "feat: something",
            author = "Maria Escobar",
            authorEmail = "maria.escobar@example.com",
        )

        val commit = assertNotNull(roundTrip(repository).getCommit(id))

        assertEquals("Maria Escobar", commit.author)
        assertEquals("maria.escobar@example.com", commit.authorEmail)
    }

    /**
     * A snapshot written before commits had authors, as a long-lived instance holds one.
     */
    @Test
    fun `A snapshot without authors still loads`() {
        val json = mapOf(
            "name" to "petclinic",
            "revision" to 1,
            "branches" to listOf(
                mapOf(
                    "name" to "main",
                    "commits" to listOf(
                        mapOf(
                            "id" to "main-1-abcdefg",
                            "revision" to 1,
                            "message" to "feat: something",
                            "timestamp" to "2026-09-01T10:15:30",
                        ),
                    ),
                ),
            ),
        ).asJson()

        val restored = MockRepository.fromData(json.parseInto(MockRepositoryData::class))

        val commit = assertNotNull(restored.getCommit("main-1-abcdefg"))
        assertEquals("unknown", commit.author)
        assertNull(commit.authorEmail)
    }

    @Test
    fun `A restored repository numbers the next commit where the snapshot stopped`() {
        val original = populated()
        val expected = original.registerCommit("main", "ISS-2 Fourth commit")

        val restored = roundTrip(populated())
        val actual = restored.registerCommit("main", "ISS-2 Fourth commit")

        assertEquals(
            expected,
            actual,
            "Commit ids are derived from the position on the branch, which the snapshot keeps",
        )
    }

    @Test
    fun `Every change to a repository is notified once`() {
        val saved = mutableListOf<MockRepositoryData>()
        val repository = MockRepository("test") { saved += it.toData() }

        repository.registerIssue("ISS-1", "Some feature", "feature")
        assertEquals(1, saved.size)

        repository.registerCommit("main", "ISS-1 First commit")
        assertEquals(2, saved.size)

        repository.registerFile("main", "some/file.txt", "Some content")
        assertEquals(3, saved.size)

        repository.createBranch("main", "feature/1")
        assertEquals(4, saved.size)

        repository.deleteBranch("feature/1")
        assertEquals(5, saved.size)

        assertEquals(
            listOf("ISS-1 First commit"),
            saved.last().branches.single().commits.map { it.message },
            "The last snapshot holds everything registered so far",
        )
    }

    @Test
    fun `Reading a repository notifies nothing`() {
        val repository = populated()
        var saves = 0
        val restored = MockRepository.fromData(repository.toData()) { saves++ }

        restored.getBranch("main")
        restored.findIssue("ISS-1")
        restored.getFile("main", "some/file.txt")
        restored.getPullRequestByName("#1")
        restored.forAllCommits { }

        assertEquals(0, saves, "Restoring and reading a repository does not write it back")
    }

    @Test
    fun `An empty repository round-trips`() {
        val restored = roundTrip(MockRepository("empty"))
        assertEquals("empty", restored.name)
        assertNull(restored.getBranch("main"))
        var commits = 0
        restored.forAllCommits { commits++ }
        assertEquals(0, commits)
    }

    /**
     * Going through JSON, and not only through [MockRepositoryData], because the JSON is what
     * [StorageMockSCMStore] actually keeps.
     */
    private fun roundTrip(repository: MockRepository): MockRepository =
        MockRepository.fromData(repository.toData().asJson().parseInto(MockRepositoryData::class))

    private fun populated(): MockRepository {
        val repository = MockRepository("petclinic")
        repository.registerIssue("ISS-1", "Some feature", "feature")
        repository.registerIssue("ISS-2", "Some fix", "fix")
        repository.registerCommit("main", "ISS-1 First commit")
        repository.registerCommit("main", "ISS-1 Second commit")
        repository.registerCommit("main", "ISS-2 Third commit")
        repository.registerFile("main", "some/file.txt", "Some content")
        repository.createBranch("main", "feature/1")
        repository.createBranch("main", "feature/2")
        repository.deleteBranch("feature/2")
        repository.createPR(
            from = "feature/1",
            to = "main",
            title = "Some PR",
            body = "Some description",
            autoApproval = false,
            reviewers = listOf("someone"),
        )
        assertTrue(repository.toData().branches.isNotEmpty(), "Fixture is populated")
        return repository
    }

}
