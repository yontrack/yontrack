package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.security.SlotView
import net.nemerosa.ontrack.extension.environments.service.graph.ProjectSlotGraphService
import net.nemerosa.ontrack.extension.environments.storage.SlotPipelineRepository
import net.nemerosa.ontrack.extension.environments.storage.SlotRepository
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SlotStatusServiceImpl(
    private val slotService: SlotService,
    private val projectSlotGraphService: ProjectSlotGraphService,
    private val slotRepository: SlotRepository,
    private val slotPipelineRepository: SlotPipelineRepository,
    private val securityService: SecurityService,
) : SlotStatusService {

    override fun isBlocked(slot: Slot): Boolean {
        val pipeline = slotService.getCurrentPipeline(slot) ?: return false
        return isBlocked(pipeline)
    }

    /**
     * Whether a deployment is held up, from the deployment itself.
     *
     * Shared by the single-slot reading and the batch one so that the two cannot answer differently:
     * "blocked" is a property of the deployment and of the phase it is in, not of how it was looked
     * up.
     */
    private fun isBlocked(pipeline: SlotPipeline): Boolean =
        when (pipeline.status) {
            // Admission rules + CANDIDATE workflows
            SlotPipelineStatus.CANDIDATE ->
                slotService.getDeploymentRunActionProgress(pipeline.id)?.ok == false
            // RUNNING workflows
            SlotPipelineStatus.RUNNING ->
                slotService.getDeploymentFinishActionProgress(pipeline.id)?.ok == false
            // Nothing in flight
            SlotPipelineStatus.DONE,
            SlotPipelineStatus.CANCELLED -> false
        }

    override fun isBehind(slot: Slot): Boolean {
        val parents = projectSlotGraphService.slotGraph(slot.project, slot.qualifier)
            .slotNodes
            .firstOrNull { it.slot.id == slot.id }
            ?.parents
            ?: return false
        if (parents.isEmpty()) return false
        val here = deployedBuildId(slot) ?: -1
        return parents.any { parent ->
            val there = deployedBuildId(parent)
            there != null && there > here
        }
    }

    /**
     * The build a slot is showing: the in-flight one when there is one, since that is what the cell
     * says it is moving to, and otherwise the last one actually deployed.
     */
    private fun deployedBuildId(slot: Slot): Int? {
        val current = slotService.getCurrentPipeline(slot)?.takeIf { !it.status.finished }?.build?.id()
        val deployed = slotService.getLastDeployedPipeline(slot)?.build?.id()
        return listOfNotNull(current, deployed).maxOrNull()
    }

    /**
     * The same reading, from pipelines already in hand.
     */
    private fun deployedBuildId(current: SlotPipeline?, deployed: SlotPipeline?): Int? =
        listOfNotNull(
            current?.takeIf { !it.status.finished }?.build?.id(),
            deployed?.build?.id(),
        ).maxOrNull()

    override fun getSlotStatuses(slots: Collection<Slot>): Map<String, SlotStatus> {
        if (slots.isEmpty()) return emptyMap()
        val asked = slots.distinctBy { it.id }.filter { securityService.isSlotAccessible<SlotView>(it) }
        if (asked.isEmpty()) return emptyMap()

        // Every slot of every project involved, because a slot which is not being asked about can
        // still be the upstream one that makes another one "behind".
        val projectIds = asked.map { it.project.id() }.toSet()
        val context = (slotRepository.findSlotsByProjectIds(projectIds) + asked)
            .distinctBy { it.id }
            .filter { securityService.isSlotAccessible<SlotView>(it) }

        val currentPipelines = slotPipelineRepository.findCurrentPipelinesBySlots(context)
        val deployedPipelines = slotPipelineRepository.findLastDeployedPipelinesBySlots(context)

        // What each slot is showing, once, so that the graph walk below is pure arithmetic.
        val shownBuildIds: Map<String, Int?> = context.associate { slot ->
            slot.id to deployedBuildId(currentPipelines[slot.id], deployedPipelines[slot.id])
        }

        // The parents of each slot, from the slots themselves: the slot graph is "the slots of the
        // same project and qualifier at the immediately lower environment order", which needs no
        // query once the slots are in hand.
        val parents: Map<String, List<Slot>> = context
            .groupBy { it.project.id() to it.qualifier }
            .values
            .flatMap { group -> group.map { slot -> slot.id to parentsOf(group, slot) } }
            .toMap()

        return asked.associate { slot ->
            val current = currentPipelines[slot.id]
            val inFlight = current?.takeIf { !it.status.finished }
            val here = shownBuildIds[slot.id] ?: -1
            slot.id to SlotStatus(
                slot = slot,
                currentPipeline = current,
                lastDeployedPipeline = deployedPipelines[slot.id],
                // Only a deployment still on its way can be blocked, so only those are checked.
                blocked = inFlight != null && isBlocked(inFlight),
                behind = parents[slot.id].orEmpty().any { parent ->
                    val there = shownBuildIds[parent.id]
                    there != null && there > here
                },
            )
        }
    }

    /**
     * The slots immediately upstream of [slot] within [group], which holds every slot of one project
     * and one qualifier.
     *
     * The same rule as
     * [net.nemerosa.ontrack.extension.environments.service.graph.ProjectSlotGraphService.slotGraph],
     * applied to slots which have already been read.
     */
    private fun parentsOf(group: List<Slot>, slot: Slot): List<Slot> {
        val lower = group.filter { it.environment.order < slot.environment.order }
        if (lower.isEmpty()) return emptyList()
        val immediate = lower.maxOf { it.environment.order }
        return lower.filter { it.environment.order == immediate }
    }

    override fun getNextBuilds(slot: Slot, count: Int): List<Build> {
        if (count <= 0) return emptyList()
        val reference = deployedBuildId(slot)
        // Eligible builds come back newest first, so every build newer than the reference is in the
        // first page: asking for `count` of them and dropping the ones which are not newer cannot
        // miss one.
        val builds = slotService.getEligibleBuilds(slot, count = count, deployable = true).pageItems
        return if (reference == null) {
            builds
        } else {
            builds.filter { it.id() > reference }
        }
    }

    override fun getBuildJourney(build: Build): List<BuildSlotJourney> {
        // Every slot of the project, with the rules refusing this build - one walk over the rules.
        val eligibleSlots = slotService.getEligibleSlotsForBuild(build)
        // Every deployment of this build, wherever it went - one query.
        val pipelinesBySlot = slotService.findPipelineByBuild(build).groupBy { it.slot.id }
        return eligibleSlots
            .map { eligibleSlot ->
                journey(
                    eligibleSlot = eligibleSlot,
                    pipelines = pipelinesBySlot[eligibleSlot.slot.id].orEmpty(),
                )
            }
            .sortedBy { it.slot.environment.order }
    }

    private fun journey(eligibleSlot: EligibleSlot, pipelines: List<SlotPipeline>): BuildSlotJourney {
        val slot = eligibleSlot.slot
        val inFlight = pipelines.filter { !it.status.finished }.maxByOrNull { it.number }
        if (inFlight != null) {
            return BuildSlotJourney(slot = slot, state = BuildSlotJourneyState.IN_PROGRESS, pipeline = inFlight)
        }
        val done = pipelines.filter { it.status == SlotPipelineStatus.DONE }.maxByOrNull { it.number }
        if (done != null) {
            val holder = slotService.getLastDeployedPipeline(slot)
            val state = if (holder != null && holder.build.id() == done.build.id()) {
                BuildSlotJourneyState.DEPLOYED
            } else {
                BuildSlotJourneyState.SUPERSEDED
            }
            return BuildSlotJourney(slot = slot, state = state, pipeline = done)
        }
        // Only cancelled deployments, or none at all: the build has never been here, so the question
        // is whether it may come.
        return BuildSlotJourney(
            slot = slot,
            state = if (eligibleSlot.eligible) BuildSlotJourneyState.ELIGIBLE else BuildSlotJourneyState.NOT_ELIGIBLE,
            pipeline = pipelines.maxByOrNull { it.number },
            nonEligibleRules = eligibleSlot.nonEligibleRules,
        )
    }

}
