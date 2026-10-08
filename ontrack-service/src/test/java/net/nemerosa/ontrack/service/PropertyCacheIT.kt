package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The properties are cached: a read from another thread while a property is being written must not
 * leave the previous value in the cache. The data must be committed, and is removed at the end.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AsAdminTest
class PropertyCacheIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

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

}
