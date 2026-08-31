package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.fail

class MetricsValidationDataTypeAliasIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")


    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `Metrics validation data type alias`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        branch:
                            validations:
                                METRICS:
                                    metrics:
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val vs = structureService.findValidationStampByName(branch.project.name, branch.name, "METRICS").getOrNull()
            ?: fail("Cannot find METRICS validation stamp")

        assertEquals(MetricsValidationDataType::class.qualifiedName, vs.dataType?.descriptor?.id)
    }

}