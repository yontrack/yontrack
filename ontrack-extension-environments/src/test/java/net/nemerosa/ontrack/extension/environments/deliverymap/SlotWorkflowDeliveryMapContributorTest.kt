package net.nemerosa.ontrack.extension.environments.deliverymap

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.environments.EnvironmentTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestFixtures
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowInstance
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNode
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.deliverymap.DeliveryMapEdgeKind
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlotWorkflowDeliveryMapContributorTest {

    private val project = Project.of(nd("P", "")).withId(ID.of(1))
    private val branch = Branch.of(project, nd("main", "")).withId(ID.of(1))
    private val build = Build.of(branch, nd("1", ""), Signature.of("test")).withId(ID.of(10))

    private val staging = EnvironmentTestFixtures.testEnvironment(name = "staging", order = 100)
    private val stagingSlot = SlotTestFixtures.testSlot(id = "slot-1", env = staging, project = project)

    private val pipeline = SlotPipeline(id = "pipeline-1", number = 1, slot = stagingSlot, build = build)

    private lateinit var slotService: SlotService
    private lateinit var slotWorkflowService: SlotWorkflowService
    private lateinit var contributor: SlotWorkflowDeliveryMapContributor

    @BeforeEach
    fun setup() {
        slotService = mockk()
        every { slotService.findSlotsByProject(project) } returns setOf(stagingSlot)
        every { slotService.getCurrentPipeline(any()) } returns null
        every { slotService.getLastDeployedPipeline(any()) } returns null

        slotWorkflowService = mockk()
        every { slotWorkflowService.getSlotWorkflowsBySlot(any()) } returns emptyList()
        every { slotWorkflowService.getSlotWorkflowInstancesByPipeline(any()) } returns emptyList()

        contributor = SlotWorkflowDeliveryMapContributor(
            slotService = slotService,
            slotWorkflowService = slotWorkflowService,
        )
    }

    @Test
    fun `A slot workflow is drawn as a checkpoint keyed on its configuration`() {
        givenSlotWorkflows(slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE))

        val checkpoint = contributor.contribute(branch).checkpoints.single()

        assertEquals("slot-workflow:sw-1", checkpoint.id)
        assertEquals(SlotDeliveryMapCheckpoints.SLOT_WORKFLOW, checkpoint.type)
        assertEquals("Smoke tests", checkpoint.name)
    }

    @Test
    fun `A candidate workflow is a gate and is required by its slot`() {
        // A CANDIDATE check which is not ok stops the deployment from starting at all
        givenSlotWorkflows(slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE))

        val edge = contributor.contribute(branch).edges.single()

        assertEquals(DeliveryMapEdgeKind.REQUIRES, edge.kind)
        assertEquals("slot-workflow:sw-1", edge.source)
        assertEquals("slot:slot-1", edge.target)
    }

    @Test
    fun `A running workflow is a gate too`() {
        // A RUNNING check which is not ok stops the deployment from finishing
        givenSlotWorkflows(slotWorkflow("sw-1", "Health check", SlotPipelineStatus.RUNNING))

        val edge = contributor.contribute(branch).edges.single()

        assertEquals(DeliveryMapEdgeKind.REQUIRES, edge.kind)
        assertEquals("slot-workflow:sw-1", edge.source)
        assertEquals("slot:slot-1", edge.target)
    }

    @Test
    fun `A done workflow gates nothing and is emitted by its slot`() {
        givenSlotWorkflows(slotWorkflow("sw-1", "Notify", SlotPipelineStatus.DONE))

        val edge = contributor.contribute(branch).edges.single()

        assertEquals(DeliveryMapEdgeKind.EMITS, edge.kind)
        assertEquals("slot:slot-1", edge.source)
        assertEquals("slot-workflow:sw-1", edge.target)
    }

    @Test
    fun `A failed workflow gates nothing and is emitted by its slot`() {
        // Like DONE, FAILED runs once the deployment is over and nothing waits for it
        givenSlotWorkflows(slotWorkflow("sw-1", "Page on-call", SlotPipelineStatus.FAILED))

        val edge = contributor.contribute(branch).edges.single()

        assertEquals(DeliveryMapEdgeKind.EMITS, edge.kind)
        assertEquals("slot:slot-1", edge.source)
        assertEquals("slot-workflow:sw-1", edge.target)
    }

    @Test
    fun `A slot with workflows on several triggers straddles its own column`() {
        givenSlotWorkflows(
            slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE),
            slotWorkflow("sw-2", "Notify", SlotPipelineStatus.DONE),
        )

        val edges = contributor.contribute(branch).edges

        assertEquals(
            setOf(
                Triple(DeliveryMapEdgeKind.REQUIRES, "slot-workflow:sw-1", "slot:slot-1"),
                Triple(DeliveryMapEdgeKind.EMITS, "slot:slot-1", "slot-workflow:sw-2"),
            ),
            edges.map { Triple(it.kind, it.source, it.target) }.toSet(),
        )
    }

    @Test
    fun `A workflow which has never run is still drawn, and so is its edge`() {
        // A CANDIDATE workflow which never ran is the reason nothing ever deployed to that slot
        givenSlotWorkflows(slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE))

        val contribution = contributor.contribute(branch)

        val data = contribution.checkpoints.single().data.parse<SlotWorkflowCheckpointData>()
        assertEquals("sw-1", data.slotWorkflowId)
        assertEquals(SlotPipelineStatus.CANDIDATE, data.trigger)
        assertNull(data.workflowInstanceId)
        assertNull(data.status)
        assertNull(data.startTime)
        assertNull(data.durationMs)
        assertEquals(1, contribution.edges.size)
    }

    @Test
    fun `A workflow which has run carries its run, its status and its duration`() {
        val slotWorkflow = slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE)
        givenSlotWorkflows(slotWorkflow)
        givenPipeline(current = pipeline, instances = listOf(slotWorkflowInstance(slotWorkflow, "i-1")))

        val data = contributor.contribute(branch).checkpoints.single()
            .data.parse<SlotWorkflowCheckpointData>()

        assertEquals("i-1", data.workflowInstanceId)
        assertEquals(WorkflowInstanceStatus.SUCCESS, data.status)
        assertEquals(START, data.startTime)
        assertEquals(2000, data.durationMs)
    }

    @Test
    fun `The current pipeline is read before the last deployed one`() {
        // Otherwise a CANDIDATE workflow could only ever be seen once it had let something through
        val slotWorkflow = slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE)
        givenSlotWorkflows(slotWorkflow)
        val deployed = SlotPipeline(id = "pipeline-0", number = 0, slot = stagingSlot, build = build)
        every { slotService.getCurrentPipeline(stagingSlot) } returns pipeline
        every { slotService.getLastDeployedPipeline(stagingSlot) } returns deployed
        every { slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline) } returns
                listOf(slotWorkflowInstance(slotWorkflow, "i-current"))
        every { slotWorkflowService.getSlotWorkflowInstancesByPipeline(deployed) } returns
                listOf(slotWorkflowInstance(slotWorkflow, "i-deployed"))

        val data = contributor.contribute(branch).checkpoints.single()
            .data.parse<SlotWorkflowCheckpointData>()

        assertEquals("i-current", data.workflowInstanceId)
    }

    @Test
    fun `A slot workflow checkpoint has no arrival of its own`() {
        // Its build would always be the one the slot beside it already names
        givenSlotWorkflows(slotWorkflow("sw-1", "Smoke tests", SlotPipelineStatus.CANDIDATE))
        assertNull(contributor.contribute(branch).checkpoints.single().arrival)
    }

    @Test
    fun `A project with no slot draws nothing`() {
        every { slotService.findSlotsByProject(project) } returns emptySet()
        val contribution = contributor.contribute(branch)
        assertTrue(contribution.checkpoints.isEmpty())
        assertTrue(contribution.edges.isEmpty())
    }

    @Test
    fun `A slot with no workflow draws nothing`() {
        val contribution = contributor.contribute(branch)
        assertTrue(contribution.checkpoints.isEmpty())
        assertTrue(contribution.edges.isEmpty())
    }

    private fun givenSlotWorkflows(vararg slotWorkflows: SlotWorkflow) {
        every { slotWorkflowService.getSlotWorkflowsBySlot(stagingSlot) } returns slotWorkflows.toList()
    }

    private fun givenPipeline(current: SlotPipeline, instances: List<SlotWorkflowInstance>) {
        every { slotService.getCurrentPipeline(stagingSlot) } returns current
        every { slotWorkflowService.getSlotWorkflowInstancesByPipeline(current) } returns instances
    }

    private fun slotWorkflow(id: String, name: String, trigger: SlotPipelineStatus) = SlotWorkflow(
        id = id,
        slot = stagingSlot,
        trigger = trigger,
        workflow = Workflow(name = name, nodes = emptyList()),
    )

    private fun slotWorkflowInstance(slotWorkflow: SlotWorkflow, instanceId: String) = SlotWorkflowInstance(
        id = "swi-$instanceId",
        start = START,
        pipeline = pipeline,
        slotWorkflow = slotWorkflow,
        workflowInstance = WorkflowInstance(
            id = instanceId,
            timestamp = START,
            workflow = slotWorkflow.workflow,
            event = SerializableEvent(
                id = 0,
                eventType = "slot-pipeline",
                signature = null,
                entities = emptyMap(),
                extraEntities = emptyMap(),
                ref = null,
                values = emptyMap(),
            ),
            contexts = emptyMap(),
            nodesExecutions = listOf(
                WorkflowInstanceNode(
                    id = "node",
                    status = WorkflowInstanceNodeStatus.SUCCESS,
                    startTime = START,
                    endTime = START.plusSeconds(2),
                    output = null,
                    error = null,
                )
            ),
        ),
    )

    companion object {
        private val START: LocalDateTime = LocalDateTime.of(2026, 9, 1, 10, 0)
    }
}
