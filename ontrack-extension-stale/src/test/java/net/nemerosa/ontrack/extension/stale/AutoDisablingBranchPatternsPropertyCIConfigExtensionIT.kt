package net.nemerosa.ontrack.extension.stale

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@AsAdminTest
class AutoDisablingBranchPatternsPropertyCIConfigExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    fun `Configuring as a default for all branches`() {
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                    defaults:
                        project:
                            auto-disabling:
                                patterns:
                                    - includes:
                                        - 'v.*'
                                      mode: DISABLE
                                      keepLast: 1
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(scmBranch = "any")
        )

        assertNotNull(getProperty(project, AutoDisablingBranchPatternsPropertyType::class.java)) {
            val item = it.items.single()
            assertEquals(listOf("v.*"), item.includes)
            assertEquals(emptyList(), item.excludes)
            assertEquals(AutoDisablingBranchPatternsMode.DISABLE, item.mode)
            assertEquals(1, item.keepLast)
        }
    }

    @Test
    fun `Configuring with a condition for the target branch`() {
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                    custom:
                        configs:
                            - conditions:
                                - name: branch
                                  config: main
                              project:
                                auto-disabling:
                                    patterns:
                                        - includes:
                                            - 'v.*'
                                          mode: DISABLE
                                          keepLast: 1
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(scmBranch = "main")
        )

        assertNotNull(getProperty(project, AutoDisablingBranchPatternsPropertyType::class.java)) {
            val item = it.items.single()
            assertEquals(listOf("v.*"), item.includes)
            assertEquals(emptyList(), item.excludes)
            assertEquals(AutoDisablingBranchPatternsMode.DISABLE, item.mode)
            assertEquals(1, item.keepLast)
        }
    }

    @Test
    fun `Setting auto-disabling property via CI config as automation user`() {
        val project = asGlobalRole(Roles.GLOBAL_AUTOMATION) {
            configTestSupport.configureProject(
                yaml = """
                    version: v1
                    configuration:
                        custom:
                            configs:
                                - conditions:
                                    - name: branch
                                      config: v24
                                  project:
                                    auto-disabling:
                                        patterns:
                                            - includes:
                                                - 'v.*'
                                              mode: DISABLE
                                              keepLast: 1
                """.trimIndent(),
                ci = "generic",
                scm = "mock",
                env = EnvFixtures.generic(scmBranch = "v24")
            )
        }

        assertNotNull(getProperty(project, AutoDisablingBranchPatternsPropertyType::class.java)) {
            val item = it.items.single()
            assertEquals(listOf("v.*"), item.includes)
            assertEquals(emptyList(), item.excludes)
            assertEquals(AutoDisablingBranchPatternsMode.DISABLE, item.mode)
            assertEquals(1, item.keepLast)
        }
    }

    @Test
    fun `Configuring with a condition for another branch`() {
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                    custom:
                        configs:
                            - conditions:
                                - name: branch
                                  config: main
                              project:
                                auto-disabling:
                                    patterns:
                                        - includes:
                                            - 'v.*'
                                          mode: DISABLE
                                          keepLast: 1
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(scmBranch = "any")
        )

        assertNull(getProperty(project, AutoDisablingBranchPatternsPropertyType::class.java))
    }

}