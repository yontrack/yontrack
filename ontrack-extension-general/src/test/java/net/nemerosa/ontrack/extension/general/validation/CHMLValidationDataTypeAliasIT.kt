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

class CHMLValidationDataTypeAliasIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")


    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `CHML validation data type alias`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                CHML:
                                    chml:
                                        warningLevel: HIGH
                                        warningValue: 1
                                        failedLevel: CRITICAL
                                        failedValue: 1
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val vs = structureService.findValidationStampByName(branch.project.name, branch.name, "CHML").getOrNull()
            ?: fail("Cannot find CHML validation stamp")

        assertEquals(CHMLValidationDataType::class.qualifiedName, vs.dataType?.descriptor?.id)
        assertIs<CHMLValidationDataTypeConfig>(vs.dataType?.config) {
            assertEquals(CHMLLevel(CHML.HIGH, 1), it.warningLevel)
            assertEquals(CHMLLevel(CHML.CRITICAL, 1), it.failedLevel)
        }
    }

}