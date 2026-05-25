package net.nemerosa.ontrack.extension.av.ci

import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.extension.av.validation.AutoVersioningValidationData
import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class AutoVersioningBranchCIConfigExtensionIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var autoVersioningConfigurationService: AutoVersioningConfigurationService

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Test
    @AsAdminTest
    fun `Auto-versioning setup`() {
        val branch = configTestSupport.configureBranch(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      autoVersioning:
                        configurations:
                          - sourceProject: my-project
                            sourceBranch: main
                            sourcePromotion: GOLD
                            targetPath: versions.properties
                            targetProperty: yontrackVersion
                            validationStamp: my-chart-validator
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic()
        )
        assertNotNull(
            autoVersioningConfigurationService.getAutoVersioning(branch),
            "Auto-versioning config has been set"
        ) { av ->
            val avConfig = av.configurations.single()
            avConfig.apply {
                assertEquals("my-project", sourceProject)
                assertEquals("main", sourceBranch)
                assertEquals("GOLD", sourcePromotion)
                assertEquals("versions.properties", targetPath)
                assertEquals("yontrackVersion", targetProperty)
                assertEquals("my-chart-validator", validationStamp)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Auto-versioning check`() {
        project {
            val dependency = this
            branch("main") {
                val pl = promotionLevel("GOLD")
                build("5.0.0") {
                    promote(pl)
                }
            }
            mockSCMTester.withMockSCMRepository(name = "yontrack") {

                repositoryFile(
                    branch = "main",
                    path = "versions.properties",
                    content = """
                        yontrackVersion = 5.0.0
                    """.trimIndent()
                )

                val build = configTestSupport.configureBuild(
                    """
                        version: v1
                        configuration:
                          defaults:
                            branch:
                              autoVersioning:
                                configurations:
                                  - sourceProject: ${dependency.name}
                                    sourceBranch: main
                                    sourcePromotion: GOLD
                                    targetPath: versions.properties
                                    targetProperty: yontrackVersion
                                    validationStamp: av-check
                            build:
                              autoVersioningCheck: true
                    """.trimIndent(),
                    ci = "generic",
                    scm = "mock",
                    env = EnvFixtures.generic(
                        scmBranch = "main",
                    )
                )

                val vs = structureService.findValidationStampByName(
                    project = build.project.name,
                    branch = build.branch.name,
                    validationStamp = "av-check"
                ).getOrNull() ?: fail("Validation stamp has not been created")

                val run = structureService.getValidationRunsForBuildAndValidationStamp(
                    build, vs, offset = 0, count = 1
                ).single()

                assertNotNull(run.data) { data ->
                    assertIs<AutoVersioningValidationData>(data.data) { avData ->
                        assertEquals(dependency.name, avData.project)
                        assertEquals("5.0.0", avData.version, "Current version OK")
                        assertEquals("5.0.0", avData.latestVersion)
                        assertEquals("versions.properties", avData.path)
                    }
                }

            }
        }
    }

    @Test
    @AsAdminTest
    fun `Auto-versioning is not disabled by default`() {
        val branch = configTestSupport.configureBranch(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      autoVersioning:
                        configurations:
                          - sourceProject: my-project
                            sourceBranch: main
                            sourcePromotion: GOLD
                            targetPath: versions.properties
                            targetProperty: yontrackVersion
                            validationStamp: my-chart-validator
                """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic()
        )
        assertNotNull(
            autoVersioningConfigurationService.getAutoVersioning(branch),
            "Auto-versioning config has been set"
        ) { av ->
            val avConfig = av.configurations.single()
            avConfig.apply {
                assertEquals("my-project", sourceProject)
                assertEquals("main", sourceBranch)
                assertEquals("GOLD", sourcePromotion)
                assertEquals("versions.properties", targetPath)
                assertEquals("yontrackVersion", targetProperty)
                assertEquals("my-chart-validator", validationStamp)
                assertEquals(null, disabled)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Enabling an auto-versioning config only for release branches`() {
        val yaml = """
            version: v1
            configuration:
              defaults:
                branch:
                  autoVersioning:
                    configurations:
                      - sourceProject: my-project
                        sourceBranch: main
                        sourcePromotion: GOLD
                        targetPath: versions.properties
                        targetProperty: yontrackVersion
                        validationStamp: my-chart-validator
                        disabled: true
              custom:
                configs:
                  - conditions:
                      - name: branch
                        config: release.*
                    branch:
                      autoVersioning:
                        configurations:
                          - sourceProject: my-project
                            sourceBranch: main
                            disabled: false
            """.trimIndent()

        val developBranch = configTestSupport.configureBranch(
            yaml,
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(scmBranch = "develop")
        )
        assertNotNull(
            autoVersioningConfigurationService.getAutoVersioning(developBranch),
            "Auto-versioning config has been set"
        ) { av ->
            val avConfig = av.configurations.single()
            avConfig.apply {
                assertEquals("my-project", sourceProject)
                assertEquals("main", sourceBranch)
                assertEquals("GOLD", sourcePromotion)
                assertEquals("versions.properties", targetPath)
                assertEquals("yontrackVersion", targetProperty)
                assertEquals("my-chart-validator", validationStamp)
                assertEquals(true, disabled)
            }
        }

        val releaseBranch = configTestSupport.configureBranch(
            yaml,
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(scmBranch = "release-1.0")
        )
        assertNotNull(
            autoVersioningConfigurationService.getAutoVersioning(releaseBranch),
            "Auto-versioning config has been set"
        ) { av ->
            val avConfig = av.configurations.single()
            avConfig.apply {
                assertEquals("my-project", sourceProject)
                assertEquals("main", sourceBranch)
                assertEquals("GOLD", sourcePromotion)
                assertEquals("versions.properties", targetPath)
                assertEquals("yontrackVersion", targetProperty)
                assertEquals("my-chart-validator", validationStamp)
                assertEquals(false, disabled)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Branch filter for the auto-versioning configuration`() {
        val yaml = """
            version: v1
            configuration:
              defaults:
                branch:
                  autoVersioning:
                    branchFilter:
                      includes:
                        - main
                        - 'release\/.*'
                      excludes:
                        - 'release\/1\..*'
                    configurations:
                      - sourceProject: my-project
                        sourceBranch: main
                        sourcePromotion: GOLD
                        targetPath: versions.properties
                        targetProperty: yontrackVersion
        """

        fun testBranch(
            scmBranch: String,
            present: Boolean,
        ) {
            val branch = configTestSupport.configureBranch(
                yaml,
                ci = "generic",
                scm = "mock",
                env = EnvFixtures.generic(scmBranch = scmBranch)
            )
            val config = autoVersioningConfigurationService.getAutoVersioning(branch)
            if (present) {
                assertNotNull(config, "Auto-versioning config has been set on branch $scmBranch")
            } else {
                assertNull(config, "Auto-versioning config has not been set on branch $scmBranch")
            }
        }

        testBranch("main", true)
        testBranch("release/1.0", false)
        testBranch("release/2.1", true)
    }

    @Test
    @AsAdminTest
    fun `pushMode is not set by default`() {
        val branch = configTestSupport.configureBranch(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      autoVersioning:
                        configurations:
                          - sourceProject: my-project
                            sourceBranch: main
                            sourcePromotion: GOLD
                            targetPath: versions.properties
                            targetProperty: yontrackVersion
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic()
        )
        assertNotNull(
            autoVersioningConfigurationService.getAutoVersioning(branch),
            "Auto-versioning config has been set"
        ) { av ->
            val avConfig = av.configurations.single()
            assertEquals(AutoVersioningPushMode.PR, avConfig.pushMode)
        }
    }

    @Test
    @AsAdminTest
    fun `pushMode can be set to PUSH`() {
        val branch = configTestSupport.configureBranch(
            """
                version: v1
                configuration:
                  defaults:
                    branch:
                      autoVersioning:
                        configurations:
                          - sourceProject: my-project
                            sourceBranch: main
                            sourcePromotion: GOLD
                            targetPath: versions.properties
                            targetProperty: yontrackVersion
                            pushMode: PUSH
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic()
        )
        assertNotNull(
            autoVersioningConfigurationService.getAutoVersioning(branch),
            "Auto-versioning config has been set"
        ) { av ->
            val avConfig = av.configurations.single()
            assertEquals(AutoVersioningPushMode.PUSH, avConfig.pushMode)
        }
    }

}
