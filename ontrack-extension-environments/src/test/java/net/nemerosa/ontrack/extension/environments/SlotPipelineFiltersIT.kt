package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The filters behind the slot page's **Deployments** tab (#1793).
 *
 * The tab offers a status, a build and a user, and they combine - so the cases below check each one
 * on its own *and* two of them together, because the previous implementation had a separate query
 * per filter and could not have combined them at all.
 */
@QueueNoAsync
@AsAdminTest
class SlotPipelineFiltersIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Test
    fun `Filtering the deployments of a slot on their status`() {
        slotTestSupport.withSlot { slot ->
            val done = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val candidate = slotTestSupport.createPipeline(slot = slot)

            assertEquals(
                listOf(candidate.id),
                slotService.findPipelines(slot, status = SlotPipelineStatus.CANDIDATE).pageItems.map { it.id },
            )
            assertEquals(
                listOf(done.id),
                slotService.findPipelines(slot, status = SlotPipelineStatus.DONE).pageItems.map { it.id },
            )
            // No status at all is not a filter: everything comes back, newest first.
            assertEquals(
                listOf(candidate.id, done.id),
                slotService.findPipelines(slot).pageItems.map { it.id },
            )
        }
    }

    /**
     * Starting a deployment cancels the one before it, so the two are created in the order that
     * leaves both alive - and the assertion is on the *set*, not on an order the cancellation
     * would have decided.
     */
    @Test
    fun `Filtering the deployments of a slot on their build`() {
        slotTestSupport.withSlot { slot ->
            val first = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val second = slotTestSupport.createRunAndFinishDeployment(slot = slot)

            assertEquals(
                listOf(first.id),
                slotService.findPipelines(slot, buildId = first.build.id()).pageItems.map { it.id },
            )
            assertEquals(
                listOf(second.id),
                slotService.findPipelines(slot, buildId = second.build.id()).pageItems.map { it.id },
            )
        }
    }

    @Test
    fun `Filtering the deployments of a slot on a user who acted on them`() {
        slotTestSupport.withSlot { slot ->
            val pipeline = slotTestSupport.createPipeline(slot = slot)
            // Whoever created it is on its audit trail; nobody else is.
            val user = slotService.getPipelineChanges(pipeline).first().user
            assertTrue(user.isNotBlank(), "The deployment records who started it")

            assertEquals(
                listOf(pipeline.id),
                slotService.findPipelines(slot, user = user).pageItems.map { it.id },
            )
            assertEquals(
                emptyList(),
                slotService.findPipelines(slot, user = "nobody-$user").pageItems.map { it.id },
            )
        }
    }

    /**
     * The point of the rewrite: a status *and* a build in the same request. The old repository chose
     * one query or the other on `buildId` and the status would have been ignored.
     */
    @Test
    fun `Filtering the deployments of a slot on a status and a build together`() {
        slotTestSupport.withSlot { slot ->
            val done = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val candidate = slotTestSupport.createPipeline(slot = slot)

            assertEquals(
                listOf(done.id),
                slotService.findPipelines(
                    slot,
                    buildId = done.build.id(),
                    status = SlotPipelineStatus.DONE,
                ).pageItems.map { it.id },
            )
            // That build's deployment is not a candidate, so this combination matches nothing -
            // which is the answer a filter must give rather than falling back on one of the two.
            assertEquals(
                emptyList(),
                slotService.findPipelines(
                    slot,
                    buildId = done.build.id(),
                    status = SlotPipelineStatus.CANDIDATE,
                ).pageItems.map { it.id },
            )
            assertEquals(
                listOf(candidate.id),
                slotService.findPipelines(
                    slot,
                    buildId = candidate.build.id(),
                    status = SlotPipelineStatus.CANDIDATE,
                ).pageItems.map { it.id },
            )
        }
    }

    @Test
    fun `Paginating the filtered deployments of a slot`() {
        slotTestSupport.withSlot { slot ->
            val first = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val second = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val third = slotTestSupport.createRunAndFinishDeployment(slot = slot)

            val page = slotService.findPipelines(slot, offset = 0, size = 2, status = SlotPipelineStatus.DONE)
            assertEquals(listOf(third.id, second.id), page.pageItems.map { it.id })
            // The total counts every match, not only the page - otherwise the table has no idea
            // there is a second page.
            assertEquals(3, page.pageInfo.totalSize)

            val next = slotService.findPipelines(slot, offset = 2, size = 2, status = SlotPipelineStatus.DONE)
            assertEquals(listOf(first.id), next.pageItems.map { it.id })
        }
    }
}
