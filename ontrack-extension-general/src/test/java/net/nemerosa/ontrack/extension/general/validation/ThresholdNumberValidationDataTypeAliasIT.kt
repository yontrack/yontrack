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
import kotlin.test.assertNull
import kotlin.test.fail

class ThresholdNumberValidationDataTypeAliasIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `Number validation data type alias with okIfGreater false`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                SECURITY.SECRETS:
                                    number:
                                        failureThreshold: 0
                                        okIfGreater: false
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val vs = structureService.findValidationStampByName(branch.project.name, branch.name, "SECURITY.SECRETS")
            .getOrNull()
            ?: fail("Cannot find SECURITY.SECRETS validation stamp")

        assertEquals(ThresholdNumberValidationDataType::class.qualifiedName, vs.dataType?.descriptor?.id)
        assertIs<ThresholdConfig>(vs.dataType?.config) {
            assertNull(it.warningThreshold)
            assertEquals(0, it.failureThreshold)
            assertEquals(false, it.okIfGreater)
        }
    }

    @Test
    @AsAdminTest
    fun `Number validation data type alias defaults okIfGreater to true`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                COVERAGE.LINES:
                                    number:
                                        warningThreshold: 1000
                                        failureThreshold: 500
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val vs = structureService.findValidationStampByName(branch.project.name, branch.name, "COVERAGE.LINES")
            .getOrNull()
            ?: fail("Cannot find COVERAGE.LINES validation stamp")

        assertEquals(ThresholdNumberValidationDataType::class.qualifiedName, vs.dataType?.descriptor?.id)
        assertIs<ThresholdConfig>(vs.dataType?.config) {
            assertEquals(1000, it.warningThreshold)
            assertEquals(500, it.failureThreshold)
            assertEquals(true, it.okIfGreater)
        }
    }

}
