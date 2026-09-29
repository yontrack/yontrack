package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.support.TestNumberValidationDataType
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.ValidationStampCreate
import net.nemerosa.ontrack.model.security.ValidationStampEdit
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@AsAdminTest
class ValidationStampIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var testNumberValidationDataType: TestNumberValidationDataType

    @Autowired
    private lateinit var predefinedValidationStampService: PredefinedValidationStampService

    @Test
    fun validationStampWithDataType() {
        // Branch
        val branch = doCreateBranch()
        // Creates a validation stamp with an associated percentage data type
        val vs = asUser().withProjectFunction(branch, ValidationStampCreate::class.java).call {
            structureService.newValidationStamp(
                    ValidationStamp.of(
                            branch,
                            NameDescription.nd("VSPercent", "")
                    ).withDataType(
                            testNumberValidationDataType.config(2)
                    )
            )
        }
        // Loads the validation stamp
        var loadedVs = asUserWithView(branch).call { structureService.getValidationStamp(vs.id) }
        // Checks the data type is still there
        @Suppress("UNCHECKED_CAST")
        var dataType: ValidationDataTypeConfig<Int?> = loadedVs.dataType as ValidationDataTypeConfig<Int?>
        assertNotNull(dataType, "Data type is loaded")
        assertEquals(TestNumberValidationDataType::class.java.name, dataType.descriptor.id)
        assertEquals(2, dataType.config)
        // Loads using the list
        val vsList = asUserWithView(branch).call { structureService.getValidationStampListForBranch(branch.id) }
        assertEquals(1, vsList.size)
        assertEquals(loadedVs.id, vsList.first().id)
        // Updates it (with a threshold)
        asUser().withProjectFunction(branch, ValidationStampEdit::class.java).execute {
            structureService.saveValidationStamp(
                    loadedVs.withDataType(
                            testNumberValidationDataType.config(60)
                    )
            )
        }
        // Reloads it and check
        loadedVs = asUserWithView(branch).call { structureService.getValidationStamp(vs.id) }
        // Checks the data type is still there
        @Suppress("UNCHECKED_CAST")
        dataType = loadedVs.dataType as ValidationDataTypeConfig<Int?>
        assertNotNull(dataType, "Data type is loaded")
        assertEquals(TestNumberValidationDataType::class.java.name, dataType.descriptor.id)
        assertEquals(60, dataType.config)
    }

    @Test
    fun `Creation from predefined with data type`() {
        // Predefined validation stamp
        val name = TestUtils.uid("PVS")
        val pvs = asAdmin().call {
            predefinedValidationStampService.newPredefinedValidationStamp(
                    PredefinedValidationStamp.of(
                            NameDescription.nd(name, "")
                    ).withDataType(
                            testNumberValidationDataType.config(50)
                    )
            )
        }
        // A branch...
        val branch = doCreateBranch()
        // Creation of validation stamp from predefined
        asUser().withProjectFunction(branch, ValidationStampCreate::class.java).execute {
            structureService.newValidationStampFromPredefined(
                    branch,
                    pvs
            )
        }
        // Gets the validation stamp
        val vs = asUser().withView(branch).call {
            structureService.findValidationStampByName(branch.project.name, branch.name, name)
        }
        // Checks
        assertPresent(vs) {
            assertEquals(name, it.name)
            assertNotNull(it.dataType) {
                assertEquals(pvs.dataType?.descriptor?.id, it.descriptor.id)
                assertEquals(50, it.config as Int)
            }
        }
    }

}