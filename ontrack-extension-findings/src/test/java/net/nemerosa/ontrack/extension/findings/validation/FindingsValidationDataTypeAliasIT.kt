package net.nemerosa.ontrack.extension.findings.validation

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.fail

class FindingsValidationDataTypeAliasIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance, so that no two tests configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `security-findings validation data type alias in the CI configuration`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                SECURITY.IMAGE:
                                    security-findings:
                                        warningLevel: HIGH
                                        warningValue: 1
                                        failedLevel: CRITICAL
                                        failedValue: 1
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val vs = structureService.findValidationStampByName(branch.project.name, branch.name, "SECURITY.IMAGE")
            .getOrNull()
            ?: fail("Cannot find SECURITY.IMAGE validation stamp")

        assertEquals(FindingsValidationDataType::class.qualifiedName, vs.dataType?.descriptor?.id)
        assertIs<CHMLValidationDataTypeConfig>(vs.dataType?.config) {
            assertEquals(CHMLLevel(CHML.HIGH, 1), it.warningLevel)
            assertEquals(CHMLLevel(CHML.CRITICAL, 1), it.failedLevel)
        }
    }
}
