package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.fail

class OnPromotionValidationDataTypeAliasIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance, so no two tests configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `Validation on promotion data type alias`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                B-SILVER:
                                    on-promotion:
                                        project: B
                                        promotion: SILVER
                                C-SILVER:
                                    on-promotion:
                                        project: C
                                        qualifier: runtime
                                        promotion: SILVER
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val b = structureService.findValidationStampByName(branch.project.name, branch.name, "B-SILVER").getOrNull()
            ?: fail("Cannot find B-SILVER validation stamp")
        assertEquals(OnPromotionValidationDataType::class.qualifiedName, b.dataType?.descriptor?.id)
        assertIs<OnPromotionValidationConfig>(b.dataType?.config) {
            assertEquals(OnPromotionValidationConfig(project = "B", qualifier = "", promotion = "SILVER"), it)
        }

        val c = structureService.findValidationStampByName(branch.project.name, branch.name, "C-SILVER").getOrNull()
            ?: fail("Cannot find C-SILVER validation stamp")
        assertIs<OnPromotionValidationConfig>(c.dataType?.config) {
            assertEquals(OnPromotionValidationConfig(project = "C", qualifier = "runtime", promotion = "SILVER"), it)
        }
    }
}
