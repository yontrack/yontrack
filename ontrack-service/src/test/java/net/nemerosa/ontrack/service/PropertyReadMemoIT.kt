package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.support.ReadMemo
import net.nemerosa.ontrack.repository.PropertyRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The properties are read once per unit of work, through its read memo, and never cached across
 * units (#2081): a unit reads what it writes, and the next unit reads what any replica wrote.
 *
 * Not transactional, so that the transactions of the tests are committed or rolled back for real.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AsAdminTest
class PropertyReadMemoIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var propertyRepository: PropertyRepository

    @Autowired
    private lateinit var queryCounter: QueryCounter

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    private fun ProjectEntity.value(): String? =
        propertyService.getPropertyValue(this, TestSimplePropertyType::class.java)?.value

    private fun ProjectEntity.edit(value: String) {
        propertyService.editProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty(value))
    }

    /**
     * Writes the property in the database only, as another replica does
     */
    private fun ProjectEntity.writtenElsewhere(value: String) {
        propertyRepository.saveProperty(
            TestSimplePropertyType::class.java.name,
            projectEntityType,
            id,
            TestSimpleProperty(value).asJson(),
        )
    }

    private fun propertiesQueries(code: () -> Unit): Int =
        queryCounter.record(code).on("PROPERTIES").filter { it.thread == Thread.currentThread().name }.size

    private fun withProject(code: (project: Project) -> Unit) {
        val project = project()
        try {
            code(project)
        } finally {
            structureService.deleteProject(project.id)
        }
    }

    @Test
    fun `Outside of a unit of work, a property is read from the database each time`() {
        withProject { project ->
            project.edit("one")
            assertEquals("one", project.value())
            project.writtenElsewhere("two")
            assertEquals("two", project.value(), "Value written elsewhere read at once")
        }
    }

    @Test
    fun `A unit of work reads the properties of an entity once`() {
        withProject { project ->
            project.edit("one")
            ReadMemo.unitOfWork {
                val queries = propertiesQueries {
                    assertEquals("one", project.value())
                    assertEquals("one", project.value())
                    propertyService.getProperties(project)
                    propertyService.hasProperty(project, TestSimplePropertyType::class.java)
                }
                assertEquals(1, queries, "One query for all the properties of the entity")
            }
        }
    }

    @Test
    fun `The next unit of work reads what was written elsewhere`() {
        withProject { project ->
            project.edit("one")
            ReadMemo.unitOfWork { assertEquals("one", project.value()) }
            project.writtenElsewhere("two")
            ReadMemo.unitOfWork { assertEquals("two", project.value()) }
        }
    }

    @Test
    fun `A property written and read in the same unit of work reads its new value`() {
        withProject { project ->
            ReadMemo.unitOfWork {
                assertNull(project.value())
                project.edit("one")
                assertEquals("one", project.value())
                project.edit("two")
                assertEquals("two", project.value())
                propertyService.deleteProperty(project, TestSimplePropertyType::class.java)
                assertNull(project.value())
            }
        }
    }

    @Test
    fun `A reaction to the commit of a property reads its new value`() {
        withProject { project ->
            project.edit("one")
            ReadMemo.unitOfWork {
                assertEquals("one", project.value())
                var seen: String? = null
                TransactionTemplate(platformTransactionManager).executeWithoutResult {
                    project.edit("two")
                    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCommit() {
                            seen = project.value()
                        }
                    })
                }
                assertEquals("two", seen)
                assertEquals("two", project.value())
            }
        }
    }

    @Test
    fun `A property written by a transaction which is rolled back is not read by the unit of work`() {
        withProject { project ->
            project.edit("one")
            ReadMemo.unitOfWork {
                assertEquals("one", project.value())
                TransactionTemplate(platformTransactionManager).executeWithoutResult { status ->
                    project.edit("two")
                    assertEquals("two", project.value(), "Read in the transaction")
                    status.setRollbackOnly()
                }
                assertEquals("one", project.value(), "Value after the rollback")
            }
        }
    }

    @Test
    fun `The properties of prefetched entities are loaded together on the first read`() {
        withProject { project ->
            val builds = project.branch<List<ProjectEntity>> {
                (1..5).map { no ->
                    build {
                        edit("build-$no")
                    }
                }
            }
            ReadMemo.unitOfWork {
                val queries = propertiesQueries {
                    propertyService.prefetchProperties(builds)
                    builds.forEachIndexed { index, build ->
                        assertEquals("build-${index + 1}", build.value())
                    }
                }
                assertEquals(1, queries, "One query for the properties of all the builds")
            }
        }
    }

    @Test
    fun `Prefetching loads nothing until a property is read`() {
        withProject { project ->
            val branches = (1..3).map { project.branch() }
            ReadMemo.unitOfWork {
                val queries = propertiesQueries {
                    propertyService.prefetchProperties(branches)
                }
                assertEquals(0, queries)
            }
        }
    }

    @Test
    fun `Prefetching outside of a unit of work does nothing`() {
        withProject { project ->
            val branches = (1..3).map { no -> project.branch { edit("branch-$no") } }
            val queries = propertiesQueries {
                propertyService.prefetchProperties(branches)
                branches.forEachIndexed { index, branch ->
                    assertEquals("branch-${index + 1}", branch.value())
                }
            }
            assertEquals(3, queries, "One query per read")
        }
    }

}
