package net.nemerosa.ontrack.extension.environments.readiness

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleData
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Build.readiness` for a slot (#2022).
 */
@AsAdminTest
class SlotReadinessIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    private val readinessQuery = """
        query Readiness(${'$'}buildId: Int!, ${'$'}promotionLevel: String, ${'$'}slotId: String) {
            build(id: ${'$'}buildId) {
                readiness(promotionLevel: ${'$'}promotionLevel, slotId: ${'$'}slotId) {
                    ready
                    missing {
                        kind
                        name
                        message
                    }
                }
            }
        }
    """

    private fun Build.readiness(slot: Slot): JsonNode =
        run(
            readinessQuery,
            mapOf("buildId" to id(), "slotId" to slot.id)
        ).path("build").path("readiness")

    private fun JsonNode.items(): List<Triple<String, String, String>> =
        path("missing").toList().map {
            Triple(it.path("kind").asString(), it.path("name").asString(), it.path("message").asString())
        }

    private fun JsonNode.kindsAndNames(): List<Pair<String, String>> =
        items().map { (kind, name, _) -> kind to name }

    /**
     * A slot requiring GOLD and a manual approval.
     */
    private fun withGoldAndManualSlot(code: (slot: Slot, manual: SlotAdmissionRuleConfig) -> Unit) {
        slotTestSupport.withSlot { slot ->
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(
                    slot = slot,
                    name = "gold",
                    promotion = "GOLD",
                )
            )
            val manual = SlotAdmissionRuleConfig(
                slot = slot,
                name = "approval",
                description = "Approval by the release manager",
                ruleId = ManualApprovalSlotAdmissionRule.ID,
                ruleConfig = ManualApprovalSlotAdmissionRuleConfig(message = "Approval required").asJson(),
            )
            slotService.addAdmissionRuleConfig(manual)
            code(slot, manual)
        }
    }

    @Test
    fun `A slot with a promotion rule and a manual rule lists both`() {
        withGoldAndManualSlot { slot, _ ->
            slot.project.branch {
                promotionLevel("GOLD")
                build {
                    val readiness = readiness(slot)
                    assertFalse(readiness.path("ready").asBoolean())
                    assertEquals(
                        listOf(
                            Triple("ADMISSION_RULE", "gold", "Build not promoted"),
                            Triple(
                                "MANUAL",
                                "approval",
                                "A person must approve the deployment: the approval is given on a deployment " +
                                        "of the build, once it is started."
                            ),
                        ),
                        readiness.items()
                    )
                }
            }
        }
    }

    @Test
    fun `Once promoted, only the manual approval is missing, until it is given`() {
        withGoldAndManualSlot { slot, manual ->
            slot.project.branch {
                val gold = promotionLevel("GOLD")
                build {
                    promote(gold)
                    assertEquals(listOf("MANUAL" to "approval"), readiness(slot).kindsAndNames())
                    // A deployment, waiting for its approval
                    val pipeline = slotService.startPipeline(slot, this)
                    val waiting = readiness(slot).items().single()
                    assertEquals("MANUAL", waiting.first)
                    assertEquals(
                        "A person must approve deployment #${pipeline.number}: No approval.",
                        waiting.third
                    )
                    // Approved
                    slotService.setupAdmissionRule(
                        pipeline,
                        manual,
                        ManualApprovalSlotAdmissionRuleData(approval = true, message = "OK").asJson()
                    )
                    val readiness = readiness(slot)
                    assertTrue(readiness.path("ready").asBoolean())
                    assertTrue(readiness.path("missing").isEmpty)
                }
            }
        }
    }

    @Test
    fun `A rejected approval is still missing`() {
        withGoldAndManualSlot { slot, manual ->
            slot.project.branch {
                val gold = promotionLevel("GOLD")
                build {
                    promote(gold)
                    val pipeline = slotService.startPipeline(slot, this)
                    slotService.setupAdmissionRule(
                        pipeline,
                        manual,
                        ManualApprovalSlotAdmissionRuleData(approval = false, message = "No").asJson()
                    )
                    val (kind, name, message) = readiness(slot).items().single()
                    assertEquals("MANUAL", kind)
                    assertEquals("approval", name)
                    assertEquals("A person must approve deployment #${pipeline.number}: Rejected.", message)
                }
            }
        }
    }

    @Test
    fun `A build which is not eligible lists the refusing rule`() {
        withGoldAndManualSlot { slot, _ ->
            // No GOLD on this branch
            slot.project.branch {
                build {
                    val readiness = readiness(slot)
                    assertFalse(readiness.path("ready").asBoolean())
                    val (kind, name, message) = readiness.items().single()
                    assertEquals("ADMISSION_RULE", kind)
                    assertEquals("gold", name)
                    assertEquals("The build is not eligible for this slot: the Promotion rule refuses it.", message)
                }
            }
        }
    }

    @Test
    fun `A slot without rules is ready`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    assertTrue(readiness(slot).path("ready").asBoolean())
                }
            }
        }
    }

    @Test
    fun `A slot of another project is an input error`() {
        slotTestSupport.withSlot { slot ->
            project {
                branch {
                    build {
                        runWithMatchingError(
                            readinessQuery,
                            mapOf("buildId" to id(), "slotId" to slot.id),
                            errorClassification = ErrorType.BAD_REQUEST,
                            errorMessage = "There is no slot with ID ${slot.id} in the project ${project.name} of the build.",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `An unknown slot is an input error`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    runWithMatchingError(
                        readinessQuery,
                        mapOf("buildId" to id(), "slotId" to "unknown"),
                        errorClassification = ErrorType.BAD_REQUEST,
                        errorMessage = "There is no slot with ID unknown in the project ${project.name} of the build.",
                    )
                }
            }
        }
    }
}
