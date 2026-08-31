package net.nemerosa.ontrack.extension.config.graphql

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.config.ci.CIConfigPRNotSupportedException
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationConfig
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMProjectPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

class CIConfigurationMutationsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var buildDisplayNameService: BuildDisplayNameService

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")

    @BeforeEach
    fun init() {
        mockSCMTester.registerRepository(configuredProjectName)
    }

    @Test
    @AsAdminTest
    fun `Default configuration`() {
        configTestSupport.withConfigAndBuild(
            projectName = configuredProjectName,
            yaml = """
                version: v1
                configuration: {}
            """.trimIndent()
        ) { build, payload ->
            val buildId = payload.path("build").path("id").asInt()
            // Build SCM config
            assertNotNull(
                propertyService.getPropertyValue(build, MockSCMBuildCommitPropertyType::class.java),
                "Build SCM config has been set"
            ) {
                assertEquals(EnvFixtures.TEST_COMMIT, it.id)
            }
            // Build ID
            assertEquals(buildId, build.id())
            // Build name
            assertTrue(
                build.name.endsWith("-23"),
                "Build name ends with the build number"
            )
            // Build display name
            assertEquals(
                EnvFixtures.TEST_VERSION,
                buildDisplayNameService.getFirstBuildDisplayName(build),
                "Build has the expected version as display name"
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the branch only`() {
        configTestSupport.withBranchConfig(
            projectName = configuredProjectName,
            yaml = """
                version: v1
                configuration: {}
            """.trimIndent(),
        ) { payload ->
            val branchId = payload.path("branch").path("id").asInt()
            // Getting the branch
            val branch = structureService.getBranch(ID.of(branchId))
            assertEquals("release-5.1", branch.name)
            // Checking there is no build
            assertEquals(0, structureService.getBuildCount(branch), "No build was created")
        }
    }

    @Test
    @AsAdminTest
    fun `Branch validations`() {
        configTestSupport.withConfigAndBranch(
            projectName = configuredProjectName,
            yaml = """
                version: v1
                configuration:
                  defaults:
                      branch:
                        validations:
                          build:
                            tests: {}
            """.trimIndent()
        ) { branch, _ ->
            // Checks the validations
            val vs = structureService.getValidationStampListForBranch(branch.id)
                .singleOrNull()
                ?: fail("One and only one validation stamp is expected")
            assertEquals("build", vs.name)
            assertNotNull(vs.dataType) {
                assertEquals(
                    "net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType",
                    it.descriptor.id
                )
                assertEquals(
                    mapOf("warningIfSkipped" to false, "failWhenNoResults" to false).asJson(),
                    it.config.asJson()
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Branch promotions`() {
        configTestSupport.withConfigAndBranch(
            projectName = configuredProjectName,
            yaml = """
                version: v1
                configuration:
                  defaults:
                      branch:
                        validations:
                          build:
                            tests: {}
                        promotions:
                          BRONZE:
                            validations:
                              - build
                          SILVER:
                            promotions:
                              - BRONZE
                            validations:
                              - scan
            """.trimIndent()
        ) { branch, _ ->
            // Checks the promotions
            val promotions = structureService.getPromotionLevelListForBranch(branch.id)
            assertEquals(2, promotions.size, "Two promotions created")

            assertNotNull(
                promotions.first { it.name == "BRONZE" },
                "BRONZE promotion has been created"
            ) { pl ->
                assertNotNull(
                    propertyService.getPropertyValue(pl, AutoPromotionPropertyType::class.java),
                    "Auto promotion property has been set"
                ) { p ->
                    assertEquals(
                        listOf("build"),
                        p.validationStamps.map { it.name }
                    )
                    assertEquals(0, p.promotionLevels.size)
                }
            }

            assertNotNull(
                promotions.first { it.name == "SILVER" },
                "SILVER promotion has been created"
            ) { pl ->
                assertNotNull(
                    propertyService.getPropertyValue(pl, AutoPromotionPropertyType::class.java),
                    "Auto promotion property has been set"
                ) { p ->
                    assertEquals(
                        listOf("scan"),
                        p.validationStamps.map { it.name }
                    )
                    assertEquals(
                        listOf("BRONZE"),
                        p.promotionLevels.map { it.name }
                    )
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Branch validations with positive condition`() {
        configTestSupport.withConfigAndBranch(
            projectName = configuredProjectName,
            yaml = """
                version: v1
                configuration:
                  defaults:
                      branch:
                        validations:
                          build:
                            tests: {}
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: release.*
                        branch:
                          validations:
                            deploy-tests:
                              tests:
                                warningIfSkipped: true
            """.trimIndent()
        ) { branch, _ ->
            val vss = structureService.getValidationStampListForBranch(branch.id)
            assertEquals(2, vss.size, "Two validation stamps created")

            assertNotNull(
                vss.first { it.name == "build" },
                "Build validation stamp has been created"
            ) {
                assertEquals(
                    TestSummaryValidationDataType::class.qualifiedName,
                    it.dataType?.descriptor?.id
                )
                assertEquals(
                    TestSummaryValidationConfig(
                        warningIfSkipped = false,
                        failWhenNoResults = false,
                    ),
                    it.dataType?.config
                )
            }

            assertNotNull(
                vss.first { it.name == "deploy-tests" },
                "Deployment tests validation stamp has been created"
            ) {
                assertEquals(
                    TestSummaryValidationDataType::class.qualifiedName,
                    it.dataType?.descriptor?.id
                )
                assertEquals(
                    TestSummaryValidationConfig(
                        warningIfSkipped = true,
                        failWhenNoResults = false,
                    ),
                    it.dataType?.config
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Project issue service identifier`() {
        configTestSupport.withConfigAndProject(
            projectName = configuredProjectName,
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      issueServiceIdentifier:
                        serviceId: jira
                        serviceName: JIRA
            """.trimIndent()
        ) { project, _ ->
            assertNotNull(
                propertyService.getPropertyValue(project, MockSCMProjectPropertyType::class.java),
                "Project SCM config has been set"
            ) { property ->
                assertEquals(
                    "jira//JIRA",
                    property.issueServiceIdentifier
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `PR are not ingested`() {
        assertFailsWith<CIConfigPRNotSupportedException> {
            configTestSupport.configureBranch(
                ci = "generic",
                scm = "mock",
                env = EnvFixtures.generic(configuredProjectName, scmBranch = "PR-2")
            )
        }
    }
}
