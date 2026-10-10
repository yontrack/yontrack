package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestProperty
import net.nemerosa.ontrack.extension.api.support.TestPropertyType
import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.PropertyRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

@AsAdminTest
class PropertyServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var propertyRepository: PropertyRepository

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Test
    fun `Has property`() {
        project {
            assertFalse(propertyService.hasProperty(this, TestSimplePropertyType::class.java), "No property set yet")
            // Setting the property
            asUser().withProjectFunction(this, ProjectEdit::class.java).call {
                propertyService.editProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("my-value"))
                assertTrue(propertyService.hasProperty(this, TestSimplePropertyType::class.java), "Property is now set")
            }
        }
    }

    @Test
    fun `Properties of an entity are returned when one of them cannot be read`() {
        project {
            propertyService.editProperty(this, TestSimplePropertyType::class.java, TestSimpleProperty("my-value"))
            storeUnreadableProperty()

            val properties = propertyService.getProperties(this)

            val simple = properties.first { it.type is TestSimplePropertyType }
            assertEquals("my-value", (simple.value as TestSimpleProperty).value)
            assertNull(simple.error)
            assertFalse(simple.hasError)

            val unreadable = properties.first { it.type is TestPropertyType }
            assertNull(unreadable.value, "No value for an unreadable property")
            assertTrue(unreadable.hasError)
            val error = assertNotNull(unreadable.error)
            assertTrue(error.isNotBlank(), "Error is filled in")
            assertFalse(error.contains(SECRET), "Error does not contain the stored value")
            assertTrue(unreadable.editable, "Unreadable property is still editable")
        }
    }

    @Test
    fun `Single property by name returns an error when it cannot be read`() {
        project {
            storeUnreadableProperty()
            val property = propertyService.getProperty<TestProperty>(this, TestPropertyType::class.java.name)
            assertNull(property.value)
            assertNotNull(property.error)
        }
    }

    @Test
    fun `Asking for the value of a property which cannot be read still fails`() {
        project {
            storeUnreadableProperty()
            assertFails {
                propertyService.getPropertyValue(this, TestPropertyType::class.java)
            }
        }
    }

    @Test
    fun `Deleting a property which cannot be read`() {
        project {
            storeUnreadableProperty()
            val ack = propertyService.deleteProperty(this, TestPropertyType::class.java)
            assertTrue(ack.success, "Property deleted")
            assertFalse(propertyService.hasProperty(this, TestPropertyType::class.java), "Property is gone")
            assertNotNull(
                eventQueryService.getLastEvent(this, EventFactory.PROPERTY_DELETE),
                "Deletion event posted"
            )
        }
    }

    @Test
    fun `Replacing a property which cannot be read`() {
        project {
            storeUnreadableProperty()
            propertyService.editProperty(this, TestPropertyType::class.java, TestProperty.of("fixed"))
            val property = propertyService.getProperties(this).first { it.type is TestPropertyType }
            assertNull(property.error, "Error is gone")
            assertEquals("fixed", (property.value as TestProperty).value)
        }
    }

    /**
     * Stores a [TestPropertyType] value which misses its required `value` field.
     */
    private fun Project.storeUnreadableProperty() {
        propertyRepository.saveProperty(
            TestPropertyType::class.java.name,
            projectEntityType,
            id,
            mapOf(
                "configuration" to "test",
                "password" to SECRET,
            ).asJson()
        )
    }

    companion object {
        private const val SECRET = "s3cr3t-stored-value"
    }

}
