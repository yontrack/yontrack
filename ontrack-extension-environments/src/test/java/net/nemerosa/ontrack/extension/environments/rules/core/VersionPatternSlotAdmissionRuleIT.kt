package net.nemerosa.ontrack.extension.environments.rules.core

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfigException
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.general.BuildLinkDisplayProperty
import net.nemerosa.ontrack.extension.general.BuildLinkDisplayPropertyType
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@AsAdminTest
class VersionPatternSlotAdmissionRuleIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var versionPatternSlotAdmissionRule: VersionPatternSlotAdmissionRule

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    /**
     * GA and beta releases, not alphas nor release candidates - what the self.dev slot wants.
     */
    private val gaAndBetas = VersionPatternSlotAdmissionRuleConfig(
        includes = listOf("\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?"),
    )

    private fun Build.release(value: String): Build {
        setProperty(this, ReleasePropertyType::class.java, ReleaseProperty(value))
        return this
    }

    private fun Slot.versionPattern(config: VersionPatternSlotAdmissionRuleConfig) {
        slotService.addAdmissionRuleConfig(
            config = SlotAdmissionRuleConfig(
                slot = this,
                name = "versionPattern",
                description = null,
                ruleId = VersionPatternSlotAdmissionRule.ID,
                ruleConfig = config.asJson(),
            ),
        )
    }

    @Test
    fun `GA and beta releases are eligible and deployable, alphas, release candidates and unlabelled builds are not`() {
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(gaAndBetas)
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                val patch = build().release("6.0.1")
                val beta = build().release("6.0.0-beta.6")
                val alpha = build().release("6.0.0-alpha.3")
                val rc = build().release("6.0.1-rc.700")
                val unlabelled = build()

                val admitted = listOf(ga, patch, beta)
                val refused = listOf(alpha, rc, unlabelled)

                admitted.forEach {
                    assertTrue(slotService.isBuildEligible(slot, it), "${it.name} is eligible")
                    assertTrue(
                        versionPatternSlotAdmissionRule.checkBuildDeployable(it, slot, gaAndBetas).ok,
                        "${it.name} is deployable"
                    )
                }
                refused.forEach {
                    assertFalse(slotService.isBuildEligible(slot, it), "${it.name} is not eligible")
                    assertFalse(
                        versionPatternSlotAdmissionRule.checkBuildDeployable(it, slot, gaAndBetas).ok,
                        "${it.name} is not deployable"
                    )
                }

                assertEquals(
                    admitted.reversed().map { it.id },
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                    "Eligible builds"
                )
                assertEquals(
                    admitted.reversed().map { it.id },
                    slotService.getEligibleBuilds(slot, deployable = true).pageItems.map { it.id },
                    "Deployable builds"
                )
            }
        }
    }

    @Test
    fun `The build name is the version when the build has no release property`() {
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(gaAndBetas)
            slot.project.branch("main") {
                val named = build("6.0.0")
                val other = build("build-12")
                assertTrue(slotService.isBuildEligible(slot, named))
                assertFalse(slotService.isBuildEligible(slot, other))
                assertEquals(
                    listOf(named.id),
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `The release property wins over the build name`() {
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(gaAndBetas)
            slot.project.branch("main") {
                val build = build("6.0.0").release("6.0.0-alpha.1")
                assertFalse(slotService.isBuildEligible(slot, build))
                assertEquals(emptyList(), slotService.getEligibleBuilds(slot).pageItems)
            }
        }
    }

    @Test
    fun `The release property is the version even when the project does not display it`() {
        slotTestSupport.withSlot { slot ->
            setProperty(slot.project, BuildLinkDisplayPropertyType::class.java, BuildLinkDisplayProperty(useLabel = false))
            slot.versionPattern(gaAndBetas)
            slot.project.branch("main") {
                val alpha = build("6.0.0").release("6.0.0-alpha.1")
                val ga = build("build-12").release("6.0.0")
                assertFalse(slotService.isBuildEligible(slot, alpha))
                assertTrue(slotService.isBuildEligible(slot, ga))
                assertEquals(
                    listOf(ga.id),
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `Excludes win over includes`() {
        val config = VersionPatternSlotAdmissionRuleConfig(
            includes = listOf("6\\..*"),
            excludes = listOf(".*-alpha\\..*"),
        )
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(config)
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                val alpha = build().release("6.0.0-alpha.3")
                assertTrue(slotService.isBuildEligible(slot, ga))
                assertFalse(slotService.isBuildEligible(slot, alpha))
                assertEquals(
                    listOf(ga.id),
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `Excludes alone apply to every version`() {
        val config = VersionPatternSlotAdmissionRuleConfig(
            excludes = listOf(".*-alpha\\..*"),
        )
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(config)
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                val alpha = build().release("6.0.0-alpha.3")
                assertTrue(slotService.isBuildEligible(slot, ga))
                assertFalse(slotService.isBuildEligible(slot, alpha))
                assertEquals(
                    listOf(ga.id),
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `An empty configuration admits every build`() {
        val config = VersionPatternSlotAdmissionRuleConfig()
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(config)
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                val alpha = build().release("6.0.0-alpha.3")
                val unlabelled = build()
                listOf(ga, alpha, unlabelled).forEach {
                    assertTrue(slotService.isBuildEligible(slot, it), "${it.name} is eligible")
                }
                assertEquals(
                    listOf(unlabelled, alpha, ga).map { it.id },
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `Patterns are full matches`() {
        val config = VersionPatternSlotAdmissionRuleConfig(
            includes = listOf("\\d+\\.\\d+\\.\\d+"),
        )
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(config)
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                // Would match an unanchored `~` in PostgreSQL
                val alpha = build().release("6.0.0-alpha.3")
                val prefixed = build().release("v6.0.0")
                assertTrue(slotService.isBuildEligible(slot, ga))
                assertFalse(slotService.isBuildEligible(slot, alpha))
                assertFalse(slotService.isBuildEligible(slot, prefixed))
                assertEquals(
                    listOf(ga.id),
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `Two version pattern rules on one slot both apply`() {
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(VersionPatternSlotAdmissionRuleConfig(includes = listOf("6\\..*")))
            slot.versionPattern(VersionPatternSlotAdmissionRuleConfig(excludes = listOf(".*-alpha\\..*")))
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                /* val alpha = */ build().release("6.0.0-alpha.3")
                /* val old = */ build().release("5.5.0")
                assertEquals(
                    listOf(ga.id),
                    slotService.getEligibleBuilds(slot).pageItems.map { it.id },
                )
            }
        }
    }

    @Test
    fun `Refusal names the version and the includes it does not match`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch("main") {
                val alpha = build().release("6.0.0-alpha.3")
                val check = versionPatternSlotAdmissionRule.checkBuildDeployable(alpha, slot, gaAndBetas)
                assertFalse(check.ok)
                assertEquals(
                    "Build version 6.0.0-alpha.3 matches none of `\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?`",
                    check.reason,
                )
            }
        }
    }

    @Test
    fun `Refusal names the version and the exclude it matches`() {
        val config = VersionPatternSlotAdmissionRuleConfig(excludes = listOf(".*-rc\\..*", ".*-alpha\\..*"))
        slotTestSupport.withSlot { slot ->
            slot.project.branch("main") {
                val alpha = build().release("6.0.0-alpha.3")
                val check = versionPatternSlotAdmissionRule.checkBuildDeployable(alpha, slot, config)
                assertFalse(check.ok)
                assertEquals("Build version 6.0.0-alpha.3 is excluded by `.*-alpha\\..*`", check.reason)
            }
        }
    }

    @Test
    fun `A pattern valid in Kotlin but not in PostgreSQL is refused`() {
        // Possessive quantifiers and inline flags exist in Java only: in the eligible-builds query
        // they would fail every listing of the slot
        listOf("\\d++", "6\\..*(?i)beta").forEach { pattern ->
            assertTrue(runCatching { pattern.toRegex() }.isSuccess, "$pattern compiles in Kotlin")
            assertFailsWith<SlotAdmissionRuleConfigException>(pattern) {
                versionPatternSlotAdmissionRule.checkConfig(
                    VersionPatternSlotAdmissionRuleConfig(includes = listOf(pattern)).asJson()
                )
            }
        }
        versionPatternSlotAdmissionRule.checkConfig(gaAndBetas.asJson())
    }

    @Test
    fun `A pipeline of a GA release can be deployed`() {
        slotTestSupport.withSlot { slot ->
            slot.versionPattern(gaAndBetas)
            slot.project.branch("main") {
                val ga = build().release("6.0.0")
                val pipeline = slotService.startPipeline(slot, ga)
                assertTrue(slotService.runDeployment(pipeline.id, dryRun = true).ok, "GA can be deployed")
            }
        }
    }

}
