package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.assertIs
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.fail

class ThresholdPercentageValidationDataTypeAliasIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")


    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `Percentage validation data type alias`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                PERCENTAGE:
                                    percentage:
                                        okIfGreater: false
                                        warningThreshold: 50
                                        failureThreshold: 80
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val vs = structureService.findValidationStampByName(branch.project.name, branch.name, "PERCENTAGE").getOrNull()
            ?: fail("Cannot find PERCENTAGE validation stamp")

        assertEquals(ThresholdPercentageValidationDataType::class.qualifiedName, vs.dataType?.descriptor?.id)
        assertIs<ThresholdConfig>(vs.dataType?.config) {
            assertEquals(50, it.warningThreshold)
            assertEquals(80, it.failureThreshold)
            assertEquals(false, it.okIfGreater)
        }
    }

}