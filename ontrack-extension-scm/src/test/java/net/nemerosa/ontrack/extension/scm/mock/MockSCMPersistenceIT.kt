package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs

/**
 * Testing the mock SCM's optional persistence.
 *
 * A restart, or another backend replica, is simulated with a second [MockSCMExtension] on the
 * same store: a new bean reading the same store is exactly what either produces.
 */
class MockSCMPersistenceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var mockSCMStore: MockSCMStore

    @Autowired
    private lateinit var scmExtensionFeature: SCMExtensionFeature

    @Autowired
    private lateinit var issueServiceRegistry: IssueServiceRegistry

    @Test
    fun `Persistence is off by default`() {
        assertIs<InMemoryMockSCMStore>(
            mockSCMStore,
            "Integration tests create their mock data inside the test and must not pay for the writes",
        )
    }

    @Test
    fun `Repository content survives a restart`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")

        val commitId = extension(store).run {
            registerRepository(name)
            repository(name).run {
                registerIssue("ISS-1", "Some feature", "feature")
                registerFile("main", "some/file.txt", "Some content")
                registerCommit("main", "ISS-1 First commit")
            }
        }

        val repository = extension(store).repository(name)
        assertEquals("ISS-1 First commit", repository.getCommit(commitId)?.message)
        assertEquals("Some content", repository.getFile("main", "some/file.txt"))
        val issue = assertNotNull(repository.findIssue("ISS-1"), "Issue survived")
        assertEquals(commitId, issue.lastCommitId(), "Issue is still linked to its commit")
    }

    @Test
    fun `Commits registered after a restart follow the ones before it`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")

        extension(store).run {
            registerRepository(name)
            repository(name).registerCommit("main", "First commit")
        }

        val restarted = extension(store)
        val secondId = restarted.repository(name).registerCommit("main", "Second commit")

        // Same numbering as a single process would have produced
        val reference = MockSCMExtension.MockRepository(name)
        reference.registerCommit("main", "First commit")
        assertEquals(reference.registerCommit("main", "Second commit"), secondId)

        assertEquals(
            listOf("First commit", "Second commit"),
            restarted.repository(name).getBranch("main")?.commits?.map { it.message },
        )
    }

    @Test
    fun `Deleting a repository removes it from the store`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")

        extension(store).run {
            registerRepository(name)
            repository(name).registerCommit("main", "First commit")
            deleteRepository(name)
        }

        assertNull(
            extension(store).findRepository(name),
            "A deleted repository does not come back on the next start",
        )
    }

    @Test
    fun `Registering a repository again empties the stored one`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")

        extension(store).run {
            registerRepository(name)
            repository(name).registerCommit("main", "First commit")
            // As a seed re-running against a long-lived instance does
            registerRepository(name)
        }

        val restarted = extension(store)
        assertNull(
            restarted.repository(name).getBranch("main"),
            "The commits of the previous registration are gone from the store too",
        )
    }

    @Test
    fun `A repository the store cannot read is ignored`() {
        val store = StorageMockSCMStore(storageService)
        val readable = uid("repo-")
        val unreadable = uid("repo-")

        extension(store).run {
            registerRepository(readable)
            repository(readable).registerCommit("main", "First commit")
        }
        // As a version of Yontrack changing the snapshot would leave behind
        storageService.storeJson(
            StorageMockSCMStore.STORE,
            unreadable,
            mapOf("name" to unreadable, "schema" to 99).asJson(),
        )

        val restarted = extension(store)
        assertNotNull(
            restarted.findRepository(readable),
            "One unreadable repository does not cost the others",
        )
        assertNull(
            restarted.findRepository(unreadable),
            "What cannot be read is skipped, and the mock SCM starts without it",
        )
    }

    @Test
    fun `A repository registered through another backend is seen by one which read the store before`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")
        val backendA = extension(store)
        val backendB = extension(store)

        // Backend B reads the store first, before the repository exists
        assertNull(backendB.findRepository(name))

        backendA.registerRepository(name)
        val commitId = backendA.repository(name).registerCommit("main", "First commit")

        assertEquals(
            "First commit",
            backendB.repository(name).getCommit(commitId)?.message,
            "The repository and its commit are seen by the other backend (#2099)",
        )
    }

    @Test
    fun `Commits registered through two backends follow one another`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")
        val backendA = extension(store)
        val backendB = extension(store)

        backendA.registerRepository(name)
        val firstId = backendA.repository(name).registerCommit("main", "First commit")
        // Backend B reads the repository...
        assertEquals("First commit", backendB.repository(name).getCommit(firstId)?.message)
        // ... before backend A adds to it
        val secondId = backendA.repository(name).registerCommit("main", "Second commit")
        assertEquals("Second commit", backendB.repository(name).getCommit(secondId)?.message)
        val thirdId = backendB.repository(name).registerCommit("main", "Third commit")

        // Same numbering as a single process would have produced
        val reference = MockSCMExtension.MockRepository(name)
        reference.registerCommit("main", "First commit")
        reference.registerCommit("main", "Second commit")
        assertEquals(reference.registerCommit("main", "Third commit"), thirdId)

        assertEquals(
            listOf("First commit", "Second commit", "Third commit"),
            backendA.repository(name).getBranch("main")?.commits?.map { it.message },
        )
    }

    @Test
    fun `A repository deleted through another backend is gone from all of them`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")
        val backendA = extension(store)
        val backendB = extension(store)

        backendA.registerRepository(name)
        backendA.repository(name).registerCommit("main", "First commit")
        assertNotNull(backendB.findRepository(name))

        backendA.deleteRepository(name)

        assertNull(backendB.findRepository(name), "The deletion is seen by the other backend")
    }

    @Test
    fun `A repository registered again through another backend is empty on all of them`() {
        val store = StorageMockSCMStore(storageService)
        val name = uid("repo-")
        val backendA = extension(store)
        val backendB = extension(store)

        backendA.registerRepository(name)
        backendA.repository(name).registerCommit("main", "First commit")
        assertNotNull(backendB.repository(name).getBranch("main"))

        // As a demo reset does, through whichever backend answers
        backendA.registerRepository(name)

        assertNull(
            backendB.repository(name).getBranch("main"),
            "The commits of the previous registration are gone from the other backend too",
        )
    }

    private fun extension(store: MockSCMStore) = MockSCMExtension(
        extensionFeature = scmExtensionFeature,
        propertyService = propertyService,
        structureService = structureService,
        issueServiceRegistry = issueServiceRegistry,
        mockSCMStore = store,
    )

}
