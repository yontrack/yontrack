package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Entries of the deployments of a build.
 */
@QueueNoAsync
@OptIn(ExperimentalTime::class)
class DeploymentTrailListenerIT : AbstractTrailListenerITSupport() {

    @Autowired
    private lateinit var environmentService: EnvironmentService

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Test
    fun `Starting a deployment writes deployment created`() {
        withSlotAndBuild { slot, build ->
            val (result, actor) = asCi {
                slotService.startPipeline(slot, build) to securityService.currentSignature.user.name
            }
            val (pipeline, user) = result
            build.trailAfter(1).assertSingleEntry(
                TrailEntryTypes.DEPLOYMENT_CREATED,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "message" to "Pipeline started",
                    "claimed" to claimed(pipeline.start, user),
                ),
                actor,
            )
        }
    }

    @Test
    fun `Running and finishing a back-dated deployment writes deployment running and done, their times claimed`() {
        withSlotAndBuild(created = LocalDateTime.of(2026, 9, 1, 8, 0, 0)) { slot, build ->
            val started = LocalDateTime.of(2026, 9, 2, 8, 0, 0)
            val running = LocalDateTime.of(2026, 9, 2, 8, 5, 0)
            val done = LocalDateTime.of(2026, 9, 2, 8, 20, 30)
            val pipeline = slotService.startPipeline(slot, build, dateTime = started)
            val seq = build.lastSeq()
            val (user, actor) = asCi {
                assertTrue(slotService.runDeployment(pipeline.id, dateTime = running).ok, "Deployment running")
                assertTrue(slotService.finishDeployment(pipeline.id, message = "Deployed", dateTime = done).ok, "Deployment done")
                securityService.currentSignature.user.name
            }
            val (runningEntry, doneEntry) = build.trailAfter(seq).apply { assertEquals(2, size) }
            runningEntry.assertEntry(
                TrailEntryTypes.DEPLOYMENT_RUNNING,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "message" to "Deployment running",
                    "claimed" to claimed(running, user),
                ),
                actor,
            )
            doneEntry.assertEntry(
                TrailEntryTypes.DEPLOYMENT_DONE,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "message" to "Deployed",
                    "claimed" to claimed(done, user),
                ),
                actor,
            )
        }
    }

    @Test
    fun `Cancelling a deployment writes deployment cancelled`() {
        withSlotAndBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build)
            val seq = build.lastSeq()
            val (user, actor) = asCi {
                slotService.cancelPipeline(pipeline, "No longer needed")
                securityService.currentSignature.user.name
            }
            val entry = build.trailAfter(seq).single()
            entry.assertEntry(
                TrailEntryTypes.DEPLOYMENT_CANCELLED,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "message" to "No longer needed",
                    "claimed" to claimed(pipelineChangeTime(pipeline, SlotPipelineStatus.CANCELLED), user),
                ),
                actor,
            )
        }
    }

    @Test
    fun `Failing a deployment writes deployment failed`() {
        withSlotAndBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build)
            slotService.runDeployment(pipeline.id)
            val seq = build.lastSeq()
            val (user, actor) = asCi {
                slotService.failPipeline(pipeline.id, message = "Helm upgrade timed out")
                securityService.currentSignature.user.name
            }
            build.trailAfter(seq).assertSingleEntry(
                TrailEntryTypes.DEPLOYMENT_FAILED,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "message" to "Helm upgrade timed out",
                    "claimed" to claimed(pipelineChangeTime(pipeline, SlotPipelineStatus.FAILED), user),
                ),
                actor,
            )
        }
    }

    @Test
    fun `Setting the data of an admission rule writes deployment rule-data with the SHA-256 of the data`() {
        withSlotAndBuild { slot, build ->
            val config = approvalRule(slot)
            val pipeline = slotService.startPipeline(slot, build)
            val seq = build.lastSeq()
            val (user, actor) = asCi {
                slotService.setupAdmissionRule(
                    pipeline,
                    config,
                    mapOf("approval" to true, "message" to "OK").asJson()
                )
                securityService.currentSignature.user.name
            }
            val status = slotService.findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(pipeline, config.id)
            val data = assertNotNull(status?.data)
            build.trailAfter(seq).assertSingleEntry(
                TrailEntryTypes.DEPLOYMENT_RULE_DATA,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "rule" to mapOf("id" to config.id, "name" to "approval", "ruleId" to "manual"),
                    // shasum -a 256 of {"approval":true,"message":"OK"}
                    "data" to mapOf("sha256" to "a52bac90c959da15776c517ed77d0a4665d4abe42f76ebbc2c07338bb355c506"),
                    "claimed" to claimed(data.timestamp, user),
                ),
                actor,
            )
        }
    }

    @Test
    fun `Overriding an admission rule writes deployment rule-overridden with its message`() {
        withSlotAndBuild { slot, build ->
            val config = approvalRule(slot)
            val pipeline = slotService.startPipeline(slot, build)
            val seq = build.lastSeq()
            val (user, actor) = asCi {
                slotService.overrideAdmissionRule(pipeline, config, "Approved on the phone")
                securityService.currentSignature.user.name
            }
            val status = slotService.findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(pipeline, config.id)
            val override = assertNotNull(status?.override)
            build.trailAfter(seq).assertSingleEntry(
                TrailEntryTypes.DEPLOYMENT_RULE_OVERRIDDEN,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "rule" to mapOf("id" to config.id, "name" to "approval", "ruleId" to "manual"),
                    "message" to "Approved on the phone",
                    "claimed" to claimed(override.timestamp, user),
                ),
                actor,
            )
        }
    }

    @Test
    fun `Overriding a slot workflow writes deployment workflow-overridden`() {
        withSlotAndBuild { slot, build ->
            val slotWorkflow = SlotWorkflow(
                slot = slot,
                trigger = SlotPipelineStatus.RUNNING,
                workflow = Workflow(
                    name = "smoke-tests",
                    nodes = listOf(
                        WorkflowNode(
                            id = "test",
                            executorId = "mock",
                            data = mapOf("text" to "Test", "waitMs" to 0, "error" to true).asJson(),
                        )
                    ),
                ),
            )
            slotWorkflowService.addSlotWorkflow(slotWorkflow)
            val pipeline = slotService.startPipeline(slot, build)
            slotService.runDeployment(pipeline.id)
            val instance = waitForSlotWorkflow(pipeline, slotWorkflow)
            val seq = build.lastSeq()
            val (_, actor) = asCi {
                slotWorkflowService.overrideSlotWorkflowInstance(instance, "Smoke tests checked by hand")
            }
            build.trailAfter(seq).assertSingleEntry(
                TrailEntryTypes.DEPLOYMENT_WORKFLOW_OVERRIDDEN,
                mapOf(
                    "deployment" to pipeline.ref(),
                    "slotWorkflow" to mapOf(
                        "id" to slotWorkflow.id,
                        "instanceId" to instance,
                        "workflow" to "smoke-tests",
                    ),
                    "message" to "Smoke tests checked by hand",
                ),
                actor,
            )
        }
    }

    @Test
    fun `Deleting a deployment writes deployment deleted with its last status`() {
        withSlotAndBuild { slot, build ->
            val pipeline = slotService.startPipeline(slot, build)
            slotService.runDeployment(pipeline.id)
            val seq = build.lastSeq()
            val ref = pipeline.ref()
            val (_, actor) = asCi {
                slotService.deleteDeployment(pipeline.id)
            }
            build.trailAfter(seq).assertSingleEntry(
                TrailEntryTypes.DEPLOYMENT_DELETED,
                mapOf(
                    "deployment" to ref,
                    "status" to "RUNNING",
                ),
                actor,
            )
        }
    }

    @Test
    fun `A deployment moved by a workflow node is recorded in the transaction of the node`() {
        asAdmin {
            // Committed, so that the workflow, run in transactions of its own, sees the pipeline
            startNewTransaction {
                lateinit var setup: Triple<Build, SlotPipeline, JsonNode>
                withSlotAndBuild { slot, build ->
                    // Moving the pipeline to running as soon as it is created
                    slotWorkflowService.addSlotWorkflow(
                        SlotWorkflow(
                            slot = slot,
                            trigger = SlotPipelineStatus.CANDIDATE,
                            workflow = Workflow(
                                name = "auto-deploy",
                                nodes = listOf(
                                    WorkflowNode(
                                        id = "deploying",
                                        executorId = "slot-pipeline-deploying",
                                        data = mapOf<String, Any>().asJson(),
                                    )
                                ),
                            ),
                            pauseMs = 500,
                        )
                    )
                    val (pipeline, actor) = asCi {
                        slotService.startPipeline(slot, build)
                    }
                    setup = Triple(build, pipeline, actor)
                }
                setup
            } then { (build, pipeline, actor) ->
                waitUntil("Deployment running", timeout = 10.seconds, interval = 1.seconds) {
                    build.trail().any { it.type == TrailEntryTypes.DEPLOYMENT_RUNNING }
                }
                val (created, running) = build.trailAfter(1).apply { assertEquals(2, size) }
                assertEquals(TrailEntryTypes.DEPLOYMENT_CREATED, created.type)
                assertEquals(actor, created.actor, "Deployment created by the CI")
                assertEquals(TrailEntryTypes.DEPLOYMENT_RUNNING, running.type)
                assertEquals(pipeline.ref().asJson(), running.payload.path("deployment"))
                assertEquals(actor, running.actor, "The node acts with the actor who set its workflow off")
                assertTrue(running.time.isAfter(created.time), "Written later, in the transaction of the node")
            }
        }
    }

    private fun withSlotAndBuild(
        created: LocalDateTime? = null,
        code: (slot: Slot, build: Build) -> Unit,
    ) {
        asAdmin {
            val environment = Environment(name = uid("env-"), order = 1, description = null, image = false)
            environmentService.save(environment)
            project {
                val slot = Slot(environment = environment, description = null, project = this, qualifier = "")
                slotService.addSlot(slot)
                branch {
                    val build = structureService.newBuild(
                        Build.of(
                            this,
                            NameDescription.nd(uid("B"), ""),
                            created?.let { Signature.of(it, "test") } ?: securityService.currentSignature,
                        )
                    )
                    code(slot, build)
                }
            }
        }
    }

    private fun approvalRule(slot: Slot) = SlotAdmissionRuleConfig(
        slot = slot,
        name = "approval",
        description = null,
        ruleId = "manual",
        ruleConfig = mapOf("message" to "Approve the deployment?").asJson(),
    ).apply {
        slotService.addAdmissionRuleConfig(this)
    }

    private fun pipelineChangeTime(pipeline: SlotPipeline, status: SlotPipelineStatus): LocalDateTime =
        slotService.getPipelineChanges(pipeline).first { it.status == status && it.message != null }.timestamp

    private fun waitForSlotWorkflow(pipeline: SlotPipeline, slotWorkflow: SlotWorkflow): String {
        var instanceId: String? = null
        waitUntil("Slot workflow finished", timeout = 10.seconds, interval = 1.seconds) {
            val instance = slotWorkflowService.findSlotWorkflowInstanceByPipelineAndSlotWorkflow(pipeline, slotWorkflow)
            instanceId = instance?.id
            instance?.workflowInstance?.status?.finished == true
        }
        return instanceId!!
    }

    private fun SlotPipeline.ref(): Map<String, Any> {
        val number = slotService.findPipelineById(id)!!.number
        return mapOf(
            "id" to id,
            "number" to number,
            "environment" to slot.environment.name,
            "slot" to mapOf("id" to slot.id, "qualifier" to ""),
        )
    }
}
