package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.service.graph.ProjectSlotGraphService
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SlotStatusServiceImpl(
    private val slotService: SlotService,
    private val projectSlotGraphService: ProjectSlotGraphService,
) : SlotStatusService {

    override fun isBlocked(slot: Slot): Boolean {
        val pipeline = slotService.getCurrentPipeline(slot) ?: return false
        return when (pipeline.status) {
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
