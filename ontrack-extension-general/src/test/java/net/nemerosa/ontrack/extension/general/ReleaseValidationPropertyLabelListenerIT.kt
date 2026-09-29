package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertNotNull

class ReleaseValidationPropertyLabelListenerIT : AbstractGeneralExtensionTestSupport() {

    @Test
    fun `Validation on label with validation stamp already existing`() {
        project {
            branch {
                val vs = validationStamp()
                setProperty(this, ReleaseValidationPropertyType::class.java, ReleaseValidationProperty(vs.name))
                build {
                    releaseProperty(this, "2.1.0")
                    // Checks this build has been validated
                    val run = structureService.getValidationRunsForBuildAndValidationStamp(id, vs.id, 0, 1)
                        .firstOrNull()
                    assertNotNull(run, "Validation has been created")
                }
            }
        }
    }

    @Test
    fun `Validation on label with validation stamp not existing`() {
        project {
            branch {
                val vsName = uid("vs")
                setProperty(this, ReleaseValidationPropertyType::class.java, ReleaseValidationProperty(vsName))
                build {
                    releaseProperty(this, "2.1.0")
                    // Checks the validation stamp exists now
                    assertNotNull(
                        structureService.findValidationStampByName(project.name, branch.name, vsName).getOrNull(),
                        "Validation stamp has been created"
                    ) { vs ->
                        // Checks this build has been validated
                        assertNotNull(
                            structureService.getValidationRunsForBuildAndValidationStamp(id, vs.id, 0, 1)
                                .firstOrNull(),
                            "Validation has been created"
                        )
                    }
                }
            }
        }
    }

}