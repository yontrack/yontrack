package net.nemerosa.ontrack.extension.general.ci

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.general.AutoPromotionLevelPropertyType
import net.nemerosa.ontrack.extension.general.AutoValidationStampPropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AutoProjectCIConfigExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `Auto validations and promotions for all projects`() {
        val project = configTestSupport.configureProject(
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic()
        )
        assertNotNull(
            getProperty(project, AutoValidationStampPropertyType::class.java),
            "Auto validation stamps property is set"
        ) {
            assertEquals(true, it.isAutoCreate)
            assertEquals(true, it.isAutoCreateIfNotPredefined)
        }
        assertNotNull(
            getProperty(project, AutoPromotionLevelPropertyType::class.java),
            "Auto promotion levels property is set"
        ) {
            assertEquals(true, it.isAutoCreate)
        }
    }

    @Test
    @AsAdminTest
    fun `Auto validations can be overridden by an explicit project property`() {
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        project:
                            properties:
                                net.nemerosa.ontrack.extension.general.AutoValidationStampPropertyType:
                                    autoCreate: true
                                    autoCreateIfNotPredefined: false
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic()
        )
        assertNotNull(
            getProperty(project, AutoValidationStampPropertyType::class.java),
            "Auto validation stamps property is set"
        ) {
            assertEquals(true, it.isAutoCreate)
            assertEquals(
                false,
                it.isAutoCreateIfNotPredefined,
                "The explicit project property wins over the automatic setup"
            )
        }
    }

}