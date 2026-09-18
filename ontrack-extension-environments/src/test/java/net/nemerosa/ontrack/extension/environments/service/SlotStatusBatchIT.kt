package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SlotStatusService.getSlotStatuses] against the single-slot readings it must agree with.
 *
 * The batch exists because the matrix cannot afford the single-slot form; the point of these tests
 * is that it is the *same answer*, faster. Every one of them therefore asserts the batch against
 * [SlotStatusService.isBlocked] and [SlotStatusService.isBehind] rather than against a literal, so
 * that a change to either reading cannot quietly make the two disagree.
 */
@AsAdminTest
class SlotStatusBatchIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotStatusService: SlotStatusService

    @Test
    fun `The batch says what the single-slot readings say, over a whole project`() {
        environmentTestSupport.withEnvironment(order = 100) { stagingEnv ->
            environmentTestSupport.withEnvironment(order = 200) { productionEnv ->
                project {
                    val staging = slotTestSupport.slot(environment = stagingEnv, project = this)
                    val production = slotTestSupport.slot(environment = productionEnv, project = this)
                    // Production is blocked on a manual approval, and behind staging
                    slotService.addAdmissionRuleConfig(
                        SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(production)
                    )
                    val branch = branch()
                    val build1 = branch.build()
                    val build2 = branch.build()
                    slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, build1))
                    slotService.startPipeline(production, build1)
                    slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, build2))

                    val statuses = slotStatusService.getSlotStatuses(listOf(staging, production))

                    assertEquals(setOf(staging.id, production.id), statuses.keys, "Every slot answered")
                    listOf(staging, production).forEach { slot ->
                        val status = statuses.getValue(slot.id)
                        assertEquals(
                            slotStatusService.isBlocked(slot),
                            status.blocked,
                            "Batched `blocked` of ${slot.fullName()}"
                        )
                        assertEquals(
                            slotStatusService.isBehind(slot),
                            status.behind,
                            "Batched `behind` of ${slot.fullName()}"
                        )
                        assertEquals(
                            slotService.getCurrentPipeline(slot)?.id,
                            status.currentPipeline?.id,
                            "Batched current deployment of ${slot.fullName()}"
                        )
                        assertEquals(
                            slotService.getLastDeployedPipeline(slot)?.id,
                            status.lastDeployedPipeline?.id,
                            "Batched last deployed of ${slot.fullName()}"
                        )
                    }
                    // ...and the readings themselves are the interesting ones
                    assertTrue(statuses.getValue(production.id).blocked, "Production waits on an approval")
                    assertTrue(statuses.getValue(production.id).behind, "Production is behind staging")
                    assertTrue(!statuses.getValue(staging.id).behind, "Staging is the head")
                }
            }
        }
    }

    @Test
    fun `Behind is read from the whole graph, not only from the slots asked about`() {
        environmentTestSupport.withEnvironment(order = 100) { stagingEnv ->
            environmentTestSupport.withEnvironment(order = 200) { productionEnv ->
                project {
                    val staging = slotTestSupport.slot(environment = stagingEnv, project = this)
                    val production = slotTestSupport.slot(environment = productionEnv, project = this)
                    slotTestSupport.runAndFinishDeployment(
                        slotService.startPipeline(staging, branch().build())
                    )

                    // Only production is asked about - staging is not in the list at all - and it is
                    // still behind it. A column filter hiding staging must not make production look
                    // up to date.
                    val statuses = slotStatusService.getSlotStatuses(listOf(production))
                    assertEquals(setOf(production.id), statuses.keys, "Only what was asked for comes back")
                    assertTrue(statuses.getValue(production.id).behind, "Behind the slot it was not asked about")
                }
            }
        }
    }

    @Test
    fun `An idle slot is neither blocked nor holding anything`() {
        slotTestSupport.withSlot { slot ->
            val status = slotStatusService.getSlotStatuses(listOf(slot)).getValue(slot.id)
            assertTrue(!status.blocked, "Nothing in flight, nothing blocked")
            assertTrue(!status.behind, "No upstream slot")
            assertNull(status.currentPipeline, "Never had a deployment")
            assertNull(status.lastDeployedPipeline, "Never deployed")
        }
    }

    @Test
    fun `An empty batch is an empty answer`() {
        assertEquals(emptyMap(), slotStatusService.getSlotStatuses(emptyList()))
    }

}
