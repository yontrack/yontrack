package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestProperty
import net.nemerosa.ontrack.extension.api.support.TestPropertyType
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.structure.BranchCloneRequest
import net.nemerosa.ontrack.model.structure.CopyService
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
class CopyServiceIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var copyService: CopyService

    @Test
    fun `Branch cloning properties are also cloned`() {
        // Creates a branch
        val branch = doCreateBranch()

        // Sets a property on this branch
        val ack = asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            propertyService.editProperty(
                branch,
                TestPropertyType::class.java,
                TestProperty.of("Test")
            )
        }
        assertTrue(ack.success)

        // Clones the branch
        val clonedBranchName = TestUtils.uid("B")
        val clonedBranch = asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            copyService.cloneBranch(
                branch,
                BranchCloneRequest(
                    clonedBranchName,
                    emptyList()
                )
            )
        }
        assertEquals(clonedBranchName, clonedBranch.name)

        // Gets the property for the cloned branch
        val property: TestProperty? = asUserWithView(clonedBranch).call {
            propertyService.getProperty(clonedBranch, TestPropertyType::class.java).value
        }
        assertNotNull(property) {
            assertEquals("Test", it.value)
        }

    }

}
