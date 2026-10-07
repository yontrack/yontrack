package net.nemerosa.ontrack.extension.config.model

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.config.EnvFixtures.TEST_BUILD_NUMBER
import net.nemerosa.ontrack.extension.config.EnvFixtures.TEST_VERSION
import net.nemerosa.ontrack.extension.general.AgentsAdmittedProperty
import net.nemerosa.ontrack.extension.general.AgentsAdmittedPropertyType
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.extension.general.MetaInfoPropertyType
import net.nemerosa.ontrack.extension.general.PromotionDependenciesPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class CoreConfigurationServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

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
    fun `Configurable build name using a template`() {
        val build = configTestSupport.configureBuild(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    build:
                      buildName: |
                        ${'$'}{env.VERSION}-${'$'}{env.BUILD_NUMBER}-${'$'}{#.datetime?format=yyyyMMdd-HHmmss}
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName),
        )

        val name = build.name

        val regex = Regex("^${TEST_VERSION}-${TEST_BUILD_NUMBER}-[0-9]{8}-[0-9]{6}$")
        assertTrue(
            name.matches(
                regex
            ),
            "Build name [$name] matches the pattern [$regex]"
        )
    }

    @Test
    @AsAdminTest
    fun `Property configurations are templates`() {
        val build = configTestSupport.configureBuild(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    build:
                      properties:
                        net.nemerosa.ontrack.extension.general.MetaInfoPropertyType:
                          items:
                            - name: appVersion
                              value: ${'$'}{env.APP_VERSION}
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName) + mapOf("APP_VERSION" to "1.0.2"),
        )

        // Getting the meta-info
        val property = propertyService.getPropertyValue(
            build,
            MetaInfoPropertyType::class.java
        )
        assertNotNull(property) {
            assertEquals(
                "1.0.2",
                it.getValue("appVersion")
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Property configurations are templates using alias`() {
        val build = configTestSupport.configureBuild(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    build:
                      properties:
                        metaInfo:
                          - name: appVersion
                            value: ${'$'}{env.APP_VERSION}
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName) + mapOf("APP_VERSION" to "1.0.2"),
        )

        // Getting the meta-info
        val property = propertyService.getPropertyValue(
            build,
            MetaInfoPropertyType::class.java
        )
        assertNotNull(property) {
            assertEquals(
                "1.0.2",
                it.getValue("appVersion")
            )
        }
    }

    /**
     * #1639 - `autoRevoke` is nullable across the CI config layers: a layer which does not mention the flag
     * must leave the value set by an earlier one alone, instead of silently pushing it back to `false`.
     */
    @Test
    @AsAdminTest
    fun `Auto revoke is off when the promotion does not mention it`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        unit-test: {}
                      promotions:
                        BRONZE:
                          validations:
                            - unit-test
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )
        assertAutoRevoke(branch, expected = false)
    }

    @Test
    @AsAdminTest
    fun `Auto revoke is on when the promotion sets it`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        unit-test: {}
                      promotions:
                        BRONZE:
                          validations:
                            - unit-test
                          autoRevoke: true
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )
        assertAutoRevoke(branch, expected = true)
    }

    @Test
    @AsAdminTest
    fun `Auto revoke set in the defaults survives a layer which does not mention it`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        unit-test: {}
                        long-it: {}
                      promotions:
                        BRONZE:
                          validations:
                            - unit-test
                          autoRevoke: true
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: main
                        branch:
                          promotions:
                            BRONZE:
                              validations:
                                - long-it
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )
        assertAutoRevoke(branch, expected = true)
    }

    @Test
    @AsAdminTest
    fun `A later layer can turn auto revoke off`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        unit-test: {}
                      promotions:
                        BRONZE:
                          validations:
                            - unit-test
                          autoRevoke: true
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: main
                        branch:
                          promotions:
                            BRONZE:
                              autoRevoke: false
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )
        assertAutoRevoke(branch, expected = false)
    }

    private fun agentsConfig(agents: String?) = """
        version: v1
        configuration:
          defaults:
            branch:
              promotions:
                GOLD:
                  ${agents?.let { "agents: $it" } ?: "promotions: []"}
    """.trimIndent()

    private fun configureAgents(agents: String?): Branch = configTestSupport.configureBranch(
        yaml = agentsConfig(agents),
        ci = "generic",
        scm = "mock",
        env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
    )

    private fun agentsAdmitted(branch: Branch): AgentsAdmittedProperty? {
        val gold = structureService.findPromotionLevelByName(branch.project.name, branch.name, "GOLD")
            .getOrNull()
            ?: fail("Missing GOLD promotion")
        return propertyService.getPropertyValue(gold, AgentsAdmittedPropertyType::class.java)
    }

    @Test
    @AsAdminTest
    fun `Agents are admitted to a promotion with agents set to true`() {
        val branch = configureAgents("true")
        assertEquals(true, agentsAdmitted(branch)?.admitted)
    }

    @Test
    @AsAdminTest
    fun `Agents are not admitted to a promotion by default`() {
        val branch = configureAgents(null)
        assertEquals(null, agentsAdmitted(branch))
    }

    @Test
    @AsAdminTest
    fun `Agents set to false removes the admission, and leaving it out keeps it`() {
        val branch = configureAgents("true")
        assertEquals(true, agentsAdmitted(branch)?.admitted)
        configureAgents(null)
        assertEquals(true, agentsAdmitted(branch)?.admitted, "Left out, the admission is kept")
        configureAgents("false")
        assertEquals(null, agentsAdmitted(branch), "Set to false, the admission is removed")
    }

    /**
     * Checks the `autoRevoke` flag which actually made it to the auto promotion property stored on the
     * `BRONZE` promotion of the [branch].
     */
    private fun assertAutoRevoke(branch: Branch, expected: Boolean) {
        val bronze = structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE")
            .getOrNull()
            ?: fail("Missing BRONZE promotion")
        val property = propertyService.getPropertyValue(bronze, AutoPromotionPropertyType::class.java)
        assertNotNull(property) {
            assertEquals(expected, it.autoRevoke, "Auto revoke on the stored property")
        }
    }

    /**
     * The `.yontrack/ci.yaml` inversion (#1702), in miniature, and the one assertion that encodes
     * why it is written the way it is.
     *
     * `PromotionLevelConfiguration.merge` is additive only - `validations = (validations +
     * other.validations).distinct()` - so a `custom.configs` block can ADD a validation to a
     * promotion but can never remove one. "SILVER, but without DEMO.SMOKE on a release branch" is
     * therefore not expressible as an override, and the config has to be inverted instead: the
     * defaults declare the weakest SILVER - BRONZE alone, i.e. "the build is green" - and a
     * `^main$` block adds the demo verification back on top.
     *
     * What this pins is the engine, in the shape `.yontrack/ci.yaml` uses it: a `^main$` block adds
     * to the defaults, and a branch no block matches keeps the defaults as they stand. It does not
     * read `.yontrack/ci.yaml` itself - nothing does, from a test - so it cannot catch someone
     * tidying the `^main$` block back into that file. The comment there is what has to carry that,
     * and this is what tells them the tidied version could never have worked.
     */
    @Test
    @AsAdminTest
    fun `The demo verification is added to SILVER on main and not on a release branch`() {
        val yaml = """
            version: v1
            configuration:
              defaults:
                branch:
                  validations:
                    BUILD: {}
                    DEMO.SMOKE: {}
                  promotions:
                    BRONZE:
                      validations:
                        - BUILD
                    SILVER:
                      promotions:
                        - BRONZE
              custom:
                configs:
                  - conditions:
                      - name: branch
                        config: '^main${'$'}'
                    branch:
                      promotions:
                        SILVER:
                          validations:
                            - DEMO.SMOKE
        """.trimIndent()

        val main = configTestSupport.configureBranch(
            yaml = yaml,
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )
        assertSilver(main, validations = listOf("DEMO.SMOKE"))

        val patch = configTestSupport.configureBranch(
            yaml = yaml,
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "release/5.3"),
        )
        assertSilver(patch, validations = emptyList())

        // The SCM branch is `release/5.3`; the Yontrack branch is the escaped form. Both names
        // appear in the patch-release procedure and using the wrong one is the likeliest way to
        // get it subtly wrong, so it is pinned here.
        assertEquals("release-5.3", patch.name, "Yontrack branch name for release/5.3")

        // The stamp itself stays declared on the release branch, so a patch that IS demoed can be
        // stamped by hand. What changes is that SILVER no longer waits for it.
        assertTrue(
            structureService.getValidationStampListForBranch(patch.id).any { it.name == "DEMO.SMOKE" },
            "The demo verification stamp still exists on the release branch"
        )
    }

    /**
     * The `SILVER` auto-promotion property actually stored on the [branch]: always keyed on
     * `BRONZE`, and on the [validations] the branch's condition layer added to it.
     */
    private fun assertSilver(branch: Branch, validations: List<String>) {
        val silver = structureService.findPromotionLevelByName(branch.project.name, branch.name, "SILVER")
            .getOrNull()
            ?: fail("Missing SILVER promotion on ${branch.name}")
        val property = propertyService.getPropertyValue(silver, AutoPromotionPropertyType::class.java)
        assertNotNull(property) { p ->
            assertEquals(
                validations.sorted(),
                p.validationStamps.map { it.name }.sorted(),
                "SILVER validations on ${branch.name}"
            )
            assertEquals(
                listOf("BRONZE"),
                p.promotionLevels.map { it.name },
                "SILVER promotions on ${branch.name}"
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Validations and promotions additions in a condition`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        unit-test: {}
                        long-it: {}
                      promotions:
                        BRONZE:
                          validations:
                            - unit-test
                        SILVER:
                          promotions:
                            - BRONZE
                          validations:
                            - long-it
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: main
                        branch:
                          validations:
                            it-pilot: {}
                            it-live: {}
                          promotions:
                            SILVER:
                              validations:
                                - it-pilot
                                - it-live
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )

        // Checking the validations

        val vsList = structureService.getValidationStampListForBranch(branch.id)
        assertEquals(
            listOf("unit-test", "long-it", "it-pilot", "it-live").sorted(),
            vsList.map { it.name }.sorted()
        )

        // Checking the BRONZE promotion

        val bronze = structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE")
            .getOrNull()
            ?: fail("Missing BRONZE promotion")

        val bronzeValidations = propertyService.getPropertyValue(bronze, AutoPromotionPropertyType::class.java)
        assertNotNull(bronzeValidations) {
            assertEquals(listOf("unit-test"), it.validationStamps.map { it.name })
            assertEquals(emptyList(), it.promotionLevels)
        }

        // Checking the SILVER promotion

        val silver = structureService.findPromotionLevelByName(branch.project.name, branch.name, "SILVER")
            .getOrNull()
            ?: fail("Missing SILVER promotion")

        val silverValidations = propertyService.getPropertyValue(silver, AutoPromotionPropertyType::class.java)
        assertNotNull(silverValidations) { p ->
            assertEquals(
                listOf("long-it", "it-pilot", "it-live").sorted(),
                p.validationStamps.map { it.name }.sorted()
            )
            assertEquals(listOf("BRONZE"), p.promotionLevels.map { it.name })
        }
    }

    @Test
    @AsAdminTest
    fun `Promotion dependsOn sets the promotion dependencies property`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        GOLD: {}
                        target-environment:
                          dependsOn:
                            - GOLD
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName),
        )

        val gold = structureService.findPromotionLevelByName(branch.project.name, branch.name, "GOLD")
            .getOrNull()
            ?: fail("Missing GOLD promotion")

        val goldDeps = propertyService.getPropertyValue(gold, PromotionDependenciesPropertyType::class.java)
        assertEquals(null, goldDeps, "GOLD has no promotion dependencies")

        val target = structureService.findPromotionLevelByName(branch.project.name, branch.name, "target-environment")
            .getOrNull()
            ?: fail("Missing target-environment promotion")

        val targetDeps = propertyService.getPropertyValue(target, PromotionDependenciesPropertyType::class.java)
        assertNotNull(targetDeps) {
            assertEquals(listOf("GOLD"), it.dependencies)
        }
    }

    @Test
    @AsAdminTest
    fun `Fields defined in CI config are applied to promotion levels`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        BRONZE:
                          fields:
                            - name: ticket
                              displayName: Ticket
                              description: "JIRA ticket reference"
                              type: TEXT
                              required: true
                            - name: env
                              displayName: Environment
                              type: CHOICE
                              options:
                                - staging
                                - prod
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName),
        )

        val bronzeRef = structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE")
            .getOrNull()
            ?: fail("Missing BRONZE promotion")
        val bronze = structureService.getPromotionLevel(bronzeRef.id)

        assertEquals(2, bronze.fields.size)

        val ticketField = bronze.fields.find { it.name == "ticket" }
        assertNotNull(ticketField) {
            assertEquals("Ticket", it.displayName)
            assertEquals("JIRA ticket reference", it.description)
            assertEquals(net.nemerosa.ontrack.model.structure.PromotionLevelFieldType.TEXT, it.type)
            assertTrue(it.required)
            assertEquals(emptyList(), it.options)
        }

        val envField = bronze.fields.find { it.name == "env" }
        assertNotNull(envField) {
            assertEquals("Environment", it.displayName)
            assertEquals(net.nemerosa.ontrack.model.structure.PromotionLevelFieldType.CHOICE, it.type)
            assertEquals(listOf("staging", "prod"), it.options)
        }
    }

}