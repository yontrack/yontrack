package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.exceptions.ValidationStampNotFoundException
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import net.nemerosa.ontrack.model.structure.PredefinedValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@AsAdminTest
class AutoValidationStampPropertyIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var predefinedValidationStampService: PredefinedValidationStampService

    @Autowired
    private lateinit var chmlValidationDataType: CHMLValidationDataType

    @Test
    fun `Get validation stamp by name - numeric`() {
        val vs = doCreateValidationStamp()
        val vss = asUser().withProjectFunction(vs, ProjectEdit::class.java).call {
            structureService.getOrCreateValidationStamp(vs.branch, vs.name)
        }
        assertEquals(vs.id, vss.id)
    }

    @Test
    fun `Get validation stamp by name - numeric - not found`() {
        val vs = doCreateValidationStamp()
        // Gets the VS id and deletes it, to make sure it's not there any longer
        asUser().withProjectFunction(vs, ProjectEdit::class.java).call {
            structureService.deleteValidationStamp(vs.id)
        }
        // Tries to create the validation stamp by ID
        assertFailsWith<ValidationStampNotFoundException> {
            asUser().withProjectFunction(vs, ProjectEdit::class.java).call {
                structureService.getOrCreateValidationStamp(vs.branch, vs.name)
            }
        }
    }

    @Test
    fun `Get validation stamp by name - found`() {
        val vs = doCreateValidationStamp()
        val vss = asUser().withProjectFunction(vs, ProjectEdit::class.java).call {
            structureService.getOrCreateValidationStamp(vs.branch, vs.name)
        }
        assertEquals(vs.id, vss.id)
    }

    @Test
    fun `Get validation stamp by name - not found - predefined`() {
        val name = uid("PVS")
        val pvs = asUser().with(GlobalSettings::class.java).call {
            predefinedValidationStampService.newPredefinedValidationStamp(
                    PredefinedValidationStamp.of(nd(name, ""))
            )
        }
        val branch = doCreateBranch()
        asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            propertyService.editProperty(
                    branch.project,
                    AutoValidationStampPropertyType::class.java,
                    AutoValidationStampProperty(true)
            )
        }

        val vs = asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            structureService.getOrCreateValidationStamp(branch, name)
        }

        assertNotNull(vs) {
            assertEquals(pvs.name, it.name)
        }
    }

    @Test
    fun `Get validation stamp by name - not found - predefined with data type`() {
        val name = uid("PVS")
        val pvs = asUser().with(GlobalSettings::class.java).call {
            predefinedValidationStampService.newPredefinedValidationStamp(
                    PredefinedValidationStamp.of(nd(name, ""))
                            .withDataType(
                                    chmlValidationDataType.config(
                                            CHMLValidationDataTypeConfig(
                                                    CHMLLevel(CHML.HIGH, 10),
                                                    CHMLLevel(CHML.CRITICAL, 1)
                                            )
                                    )
                            )
            )
        }
        val branch = doCreateBranch()
        asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            propertyService.editProperty(
                    branch.project,
                    AutoValidationStampPropertyType::class.java,
                    AutoValidationStampProperty(true)
            )
        }

        val vs = asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            structureService.getOrCreateValidationStamp(branch, name)
        }

        assertNotNull(vs) {
            assertEquals(pvs.name, it.name)
            assertNotNull(it.dataType) { cfg ->
                assertEquals(pvs.dataType?.descriptor?.id, cfg.descriptor.id)
                assertEquals(pvs.dataType?.config, cfg.config)
            }
        }
    }

    @Test
    fun `Get validation stamp by name - not found - predefined not allowed`() {
        val name = uid("PVS")
        val branch = doCreateBranch()
        asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            propertyService.editProperty(
                    branch.project,
                    AutoValidationStampPropertyType::class.java,
                    AutoValidationStampProperty(false)
            )
        }

        assertFailsWith<ValidationStampNotFoundException> {
            asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
                structureService.getOrCreateValidationStamp(branch, name)
            }
        }
    }

    @Test
    fun `Get validation stamp by name - not found - predefined not allowed by default`() {
        val name = uid("PVS")
        val branch = doCreateBranch()
        assertFailsWith<ValidationStampNotFoundException> {
            asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
                structureService.getOrCreateValidationStamp(branch, name)
            }
        }
    }

    @Test
    fun `Get validation stamp by name - not found - not predefined`() {
        val name = uid("PVS")
        val branch = doCreateBranch()
        asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            propertyService.editProperty(
                    branch.project,
                    AutoValidationStampPropertyType::class.java,
                    AutoValidationStampProperty(true)
            )
        }

        assertFailsWith<ValidationStampNotFoundException> {
            asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
                structureService.getOrCreateValidationStamp(branch, name)
            }
        }
    }

    @Test
    fun `Get validation stamp by name - not found - not predefined - allowing creation`() {
        val name = uid("PVS")
        val branch = doCreateBranch()
        asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            propertyService.editProperty(
                    branch.project,
                    AutoValidationStampPropertyType::class.java,
                    AutoValidationStampProperty(true, true)
            )
        }

        val vs = asUser().withProjectFunction(branch, ProjectEdit::class.java).call {
            structureService.getOrCreateValidationStamp(branch, name)
        }

        assertNotNull(vs) {
            assertEquals(name, it.name)
            assertEquals("Validation automatically created on demand.", it.description)
        }
    }
}
