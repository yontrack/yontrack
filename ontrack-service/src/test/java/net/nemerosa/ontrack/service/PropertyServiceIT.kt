package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestSimpleProperty
import net.nemerosa.ontrack.extension.api.support.TestSimplePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.ProjectEdit
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AsAdminTest
class PropertyServiceIT : AbstractDSLTestSupport() {

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

}