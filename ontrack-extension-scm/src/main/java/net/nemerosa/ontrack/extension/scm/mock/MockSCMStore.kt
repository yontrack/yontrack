package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.scm.mock.MockSCMExtension.MockRepository
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the mock SCM keeps its repositories.
 *
 * The default is [InMemoryMockSCMStore], which keeps them in the memory of the backend:
 * integration and acceptance tests create their mock data inside the test and would only pay for
 * the writes. A long-lived instance configured with the mock SCM — the demo, or a local dev stack
 * whose backend is restarted after every Kotlin change — sets
 * `ontrack.config.extension.scm.mock.persistent` and gets [StorageMockSCMStore] instead, which
 * keeps nothing in memory and so is the same for every backend replica (#2099, ADR 0021).
 */
interface MockSCMStore {

    /**
     * The repository of that name, or `null` when there is none. Every change made to the
     * returned repository is handed back to [save].
     */
    fun find(name: String): MockRepository?

    /**
     * Saves a repository, replacing whatever was kept for the same name.
     */
    fun save(repository: MockRepository)

    /**
     * Removes a repository.
     */
    fun delete(name: String)
}

/**
 * Keeps the repositories in the memory of this backend, which is the mock SCM's historical
 * behaviour: they do not survive a restart, and another backend replica does not see them.
 */
class InMemoryMockSCMStore : MockSCMStore {

    private val repositories = ConcurrentHashMap<String, MockRepository>()

    override fun find(name: String): MockRepository? = repositories[name]

    override fun save(repository: MockRepository) {
        repositories[repository.name] = repository
    }

    override fun delete(name: String) {
        repositories.remove(name)
    }
}

/**
 * Keeps the repositories in the generic storage, one entry per repository, keyed by its name.
 *
 * Nothing is kept in memory: every [find] reads the repository from the storage, so that a
 * repository registered or changed through one backend is what every other backend reads next,
 * whichever one read it before (#2099).
 *
 * Each repository is written whole after every change to it. That is wasteful in a way a real
 * store would not be, and deliberately so: the mock SCM holds a demo's worth of data, and a
 * whole-snapshot write is the only kind that cannot leave the persisted repository disagreeing
 * with itself about where a branch's commits stop — which is what commit ids are derived from.
 * Like the rest of the mock SCM this assumes one writer at a time: two backends changing the same
 * repository at once keep the last write.
 */
class StorageMockSCMStore(
    private val storageService: StorageService,
) : MockSCMStore {

    /**
     * Parses the entry itself, rather than through the typed `find(store, key, type)`, so that an
     * unreadable entry reads as no repository instead of failing. That is what lets this stay
     * something no one depends on: a version of Yontrack that changes the snapshot finds the old
     * ones unparseable, logs them and goes on without them, which is the behaviour the mock SCM
     * had before it persisted anything.
     */
    override fun find(name: String): MockRepository? =
        storageService.findJson(STORE, name)?.let { json ->
            try {
                json.parseInto(MockRepositoryData::class)
            } catch (ex: Exception) {
                logger.warn("Ignoring unreadable mock SCM repository \"$name\": ${ex.message}")
                null
            }
        }?.let { data ->
            MockRepository.fromData(data, ::save)
        }

    override fun save(repository: MockRepository) {
        storageService.store(STORE, repository.name, repository.toData())
    }

    override fun delete(name: String) {
        storageService.delete(STORE, name)
    }

    companion object {
        internal const val STORE = "mock-scm"
        private val logger = LoggerFactory.getLogger(StorageMockSCMStore::class.java)
    }
}
