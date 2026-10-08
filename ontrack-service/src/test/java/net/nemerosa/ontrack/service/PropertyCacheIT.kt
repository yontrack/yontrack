package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.PropertyJdbcRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The properties are cached: a read from another thread while a property is being written must not
 * leave the previous value in the cache, neither for the reactions to the commit of this property, nor
 * after it. The data must be committed, and is removed at the end.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AsAdminTest
class PropertyCacheIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Test
    fun `A concurrent read before the commit of a property does not leave a stale value in the cache`() {
        val project = project()
        val executor = Executors.newSingleThreadExecutor()
        try {
            TransactionTemplate(platformTransactionManager).executeWithoutResult {
                propertyService.editProperty(project, TestSimplePropertyType::class.java, TestSimpleProperty("value"))
                // Read from another thread, which does not see the property yet
                val concurrent = executor.submit<TestSimpleProperty?> {
                    asAdmin { propertyService.getPropertyValue(project, TestSimplePropertyType::class.java) }
                }.get(30, TimeUnit.SECONDS)
                assertNull(concurrent, "Not committed yet")
            }
            assertEquals(
                "value",
                propertyService.getPropertyValue(project, TestSimplePropertyType::class.java)?.value,
                "Committed value"
            )
        } finally {
            executor.shutdown()
            structureService.deleteProject(project.id)
        }
    }

    @Test
    fun `A reaction to the commit of a property reads its new value, whatever the concurrent reads`() {
        withProject { project ->
            repeat(ROUNDS) { round ->
                val value = "value-$round"
                var seen: String? = null
                withConcurrentReads(project) { _ ->
                    TransactionTemplate(platformTransactionManager).executeWithoutResult {
                        propertyService.editProperty(project, TestSimplePropertyType::class.java, TestSimpleProperty(value))
                        // Like the listeners which launch some work once the property is committed
                        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                            override fun afterCommit() {
                                seen = propertyService.getPropertyValue(project, TestSimplePropertyType::class.java)?.value
                            }
                        })
                    }
                }
                assertEquals(value, seen, "Value read after the commit, round $round")
            }
        }
    }

    @Test
    fun `No stale value is left in the cache by the reads concurrent to the commit of a property`() {
        withProject { project ->
            repeat(ROUNDS) { round ->
                val value = "value-$round"
                // Reads from the database until the commit: the last ones may start before it and end after it
                withConcurrentReads(project, fromDatabase = true) { stopReads ->
                    TransactionTemplate(platformTransactionManager).executeWithoutResult {
                        propertyService.editProperty(project, TestSimplePropertyType::class.java, TestSimpleProperty(value))
                        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                            override fun afterCommit() {
                                stopReads()
                            }
                        })
                    }
                }
                assertEquals(
                    value,
                    propertyService.getPropertyValue(project, TestSimplePropertyType::class.java)?.value,
                    "Value in the cache, round $round"
                )
            }
        }
    }

    private fun withProject(code: (project: Project) -> Unit) {
        val project = project()
        try {
            code(project)
        } finally {
            structureService.deleteProject(project.id)
        }
    }

    /**
     * Runs the [code] while other threads keep reading the property of the [project].
     *
     * @param fromDatabase If set, the cache is emptied before each read
     * @param code Gets a function which stops the reads, before the end of the code
     */
    private fun withConcurrentReads(
        project: Project,
        fromDatabase: Boolean = false,
        code: (stopReads: () -> Unit) -> Unit,
    ) {
        val stop = AtomicBoolean(false)
        val started = CountDownLatch(READERS)
        val executor = Executors.newFixedThreadPool(READERS)
        try {
            val readers = List(READERS) {
                executor.submit {
                    asAdmin {
                        started.countDown()
                        while (!stop.get()) {
                            if (fromDatabase) {
                                cacheManager.getCache(PropertyJdbcRepository.CACHE_PROPERTIES)?.clear()
                            }
                            propertyService.getPropertyValue(project, TestSimplePropertyType::class.java)
                        }
                    }
                }
            }
            try {
                assertTrue(started.await(30, TimeUnit.SECONDS), "Concurrent reads started")
                code { stop.set(true) }
            } finally {
                stop.set(true)
                readers.forEach { it.get(30, TimeUnit.SECONDS) }
            }
        } finally {
            executor.shutdown()
        }
    }

    companion object {
        private const val ROUNDS = 50
        private const val READERS = 4
    }

}
