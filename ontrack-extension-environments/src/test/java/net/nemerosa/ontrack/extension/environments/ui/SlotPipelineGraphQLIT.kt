package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleData
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.getPipelineById
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestSupport
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

@QueueNoAsync
@AsAdminTest
class SlotPipelineGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowTestSupport: SlotWorkflowTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Test
    fun `Starting a pipeline`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    run(
                        """
                            mutation {
                                startSlotPipeline(input: {
                                    slotId: "${slot.id}",
                                    buildId: $id,
                                }) {
                                    pipeline {
                                        id
                                        number
                                    }
                                    errors {
                                        message
                                    }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "startSlotPipeline") { node ->
                            val pipeline = node.path("pipeline")
                            assertEquals(
                                1,
                                pipeline.path("number").asInt()
                            )
                            val pipelineId = pipeline.path("id").asText()
                            assertNotNull(slotService.findPipelineById(pipelineId), "Pipeline found") {
                                assertEquals(
                                    SlotPipelineStatus.CANDIDATE,
                                    it.status
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Getting the current pipeline for a slot`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            run(
                """
                {
                    slotById(id: "${pipeline.slot.id}") {
                        currentPipeline {
                            id
                        }
                    }
                }
            """
            ) { data ->
                assertEquals(
                    pipeline.id,
                    data.path("slotById")
                        .path("currentPipeline")
                        .path("id")
                        .asText()
                )
            }
        }
    }

    @Test
    fun `Checking if a pipeline can be deployed when there is no rule`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            runAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                            admissionRules {
                                check {
                                    ok
                                    reason
                                }
                                admissionRuleConfig {
                                    ruleId
                                    ruleConfig
                                }
                                data {
                                    user
                                    timestamp
                                    data
                                }
                                canBeOverridden
                                overridden
                                override {
                                    user
                                    message
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "runAction" to mapOf(
                                "ok" to true,
                                "overridden" to false,
                                "successCount" to 0,
                                "totalCount" to 0,
                                "percentage" to 100,
                            ),
                            "admissionRules" to emptyList<String>()
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Checking if a pipeline can be deployed with one negative rule`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            /* val pl = */ pipeline.build.branch.promotionLevel("GOLD")
            slotService.addAdmissionRuleConfig(
                config = SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(pipeline.slot),
            )
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            runAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                            admissionRules {
                                check {
                                    ok
                                    reason
                                }
                                admissionRuleConfig {
                                    ruleId
                                    ruleConfig
                                }
                                data {
                                    user
                                    timestamp
                                    data
                                }
                                canBeOverridden
                                overridden
                                override {
                                    user
                                    message
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "runAction" to mapOf(
                                "ok" to false,
                                "overridden" to false,
                                "successCount" to 0,
                                "totalCount" to 1,
                                "percentage" to 0,
                            ),
                            "admissionRules" to listOf(
                                mapOf(
                                    "check" to mapOf(
                                        "ok" to false,
                                        "reason" to "Build not promoted",
                                    ),
                                    "admissionRuleConfig" to mapOf(
                                        "ruleId" to "promotion",
                                        "ruleConfig" to mapOf(
                                            "promotion" to "GOLD"
                                        ),
                                    ),
                                    "data" to null,
                                    "canBeOverridden" to true,
                                    "overridden" to false,
                                    "override" to null,
                                )
                            )
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Checking if a pipeline can be deployed with one positive rule`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val pl = pipeline.build.branch.promotionLevel("GOLD")
            slotService.addAdmissionRuleConfig(
                config = SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(pipeline.slot),
            )
            pipeline.build.promote(pl)
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            runAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                            admissionRules {
                                check {
                                    ok
                                    reason
                                }
                                admissionRuleConfig {
                                    ruleId
                                    ruleConfig
                                }
                                data {
                                    user
                                    timestamp
                                    data
                                }
                                canBeOverridden
                                overridden
                                override {
                                    user
                                    message
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "runAction" to mapOf(
                                "ok" to true,
                                "overridden" to false,
                                "successCount" to 1,
                                "totalCount" to 1,
                                "percentage" to 100,
                            ),
                            "admissionRules" to listOf(
                                mapOf(
                                    "check" to mapOf(
                                        "ok" to true,
                                        "reason" to "Build promoted",
                                    ),
                                    "admissionRuleConfig" to mapOf(
                                        "ruleId" to "promotion",
                                        "ruleConfig" to mapOf(
                                            "promotion" to "GOLD"
                                        ),
                                    ),
                                    "data" to null,
                                    "canBeOverridden" to true,
                                    "overridden" to false,
                                    "override" to null,
                                )
                            )
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Checking if a pipeline can be deployed with one overridden rule`() {
        asAdmin {
            slotTestSupport.withSlotPipeline { pipeline ->
                /* val pl = */ pipeline.build.branch.promotionLevel("GOLD")
                val admissionRuleConfig = SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(pipeline.slot)
                slotService.addAdmissionRuleConfig(
                    config = admissionRuleConfig,
                )
                slotService.overrideAdmissionRule(
                    pipeline = pipeline,
                    admissionRuleConfig = admissionRuleConfig,
                    message = "Because I want to",
                )
                run(
                    """
                            {
                                slotPipelineById(id: "${pipeline.id}") {
                                    runAction {
                                        ok
                                        overridden
                                        successCount
                                        totalCount
                                        percentage
                                    }
                                    admissionRules {
                                        check {
                                            ok
                                            reason
                                        }
                                        admissionRuleConfig {
                                            ruleId
                                            ruleConfig
                                        }
                                        data {
                                            user
                                            timestamp
                                            data
                                        }
                                        canBeOverridden
                                        overridden
                                        override {
                                            user
                                            message
                                        }
                                    }
                                }
                            }
                        """.trimIndent()
                ) { data ->
                    assertEquals(
                        mapOf(
                            "slotPipelineById" to mapOf(
                                "runAction" to mapOf(
                                    "ok" to true,
                                    "overridden" to true,
                                    "successCount" to 1,
                                    "totalCount" to 1,
                                    "percentage" to 100,
                                ),
                                "admissionRules" to listOf(
                                    mapOf(
                                        "check" to mapOf(
                                            "ok" to true,
                                            "reason" to "Rule has been overridden",
                                        ),
                                        "admissionRuleConfig" to mapOf(
                                            "ruleId" to "promotion",
                                            "ruleConfig" to mapOf(
                                                "promotion" to "GOLD"
                                            ),
                                        ),
                                        "data" to null,
                                        "canBeOverridden" to true,
                                        "overridden" to true,
                                        "override" to mapOf(
                                            "user" to securityService.currentUser?.name,
                                            "message" to "Because I want to"
                                        ),
                                    )
                                )
                            )
                        ).asJson(),
                        data
                    )
                }
            }
        }
    }

    @Test
    fun `Getting a list of needed inputs for a pipeline`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val config = SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(pipeline.slot)
            slotService.addAdmissionRuleConfig(
                config
            )
            run(
                """
                {
                    slotPipelineById(id: "${pipeline.id}") {
                        requiredInputs {
                            config {
                                id
                                name
                                description
                                ruleId
                                ruleConfig
                            }
                        }
                    }
                }
            """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "requiredInputs" to listOf(
                                mapOf(
                                    "config" to mapOf(
                                        "id" to config.id,
                                        "name" to config.name,
                                        "description" to config.description,
                                        "ruleId" to config.ruleId,
                                        "ruleConfig" to config.ruleConfig,
                                    )
                                )
                            )
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Updating the data for a pipeline`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val config = SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(pipeline.slot)
            slotService.addAdmissionRuleConfig(config)

            assertFalse(slotService.getRequiredInputs(pipeline).isEmpty(), "Pipeline requires some input")

            run(
                """
                    mutation UpdatePipelineData(
                        ${'$'}pipelineId: String!,
                        ${'$'}values: [SlotPipelineDataInputValue!]!,
                    ) {
                        updatePipelineData(input: {
                            pipelineId: ${'$'}pipelineId,
                            values: ${'$'}values,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent(),
                mapOf(
                    "pipelineId" to pipeline.id,
                    "values" to listOf(
                        mapOf(
                            "configId" to config.id,
                            "data" to mapOf(
                                "approval" to true,
                                "message" to "OK for me"
                            )
                        )
                    )
                )
            ) { data ->
                checkGraphQLUserErrors(data, "updatePipelineData")
            }

            assertTrue(slotService.getRequiredInputs(pipeline).isEmpty(), "Pipeline doesn't require inputs any longer")

            val ruleStatus =
                slotService.getPipelineAdmissionRuleStatuses(pipeline)
                    .find { it.admissionRuleConfig.id == config.id }

            assertNotNull(ruleStatus, "Rule status data") {
                val data = it.data?.data?.parse<ManualApprovalSlotAdmissionRuleData>()
                assertEquals(
                    ManualApprovalSlotAdmissionRuleData(
                        approval = true,
                        message = "OK for me"
                    ),
                    data
                )
            }
        }
    }

    @Test
    fun `Deployment run action not possible if already running`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            slotService.runDeployment(pipeline.id, dryRun = false)
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            runAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "runAction" to null,
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Deployment finish action not possible if only started`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            finishAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "finishAction" to null,
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Deployment finish action not possible if already finished`() {
        slotTestSupport.withFinishedDeployment { pipeline ->
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            finishAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "finishAction" to null,
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Deployment finish action OK when running and no workflows`() {
        slotTestSupport.withRunningDeployment { pipeline ->
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            finishAction {
                                ok
                                overridden
                                successCount
                                totalCount
                                percentage
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "slotPipelineById" to mapOf(
                            "finishAction" to mapOf(
                                "ok" to true,
                                "overridden" to false,
                                "successCount" to 0,
                                "totalCount" to 0,
                                "percentage" to 100,
                            ),
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

    @Test
    fun `Deployment finish action OK when running and one successful workflow`() {
        forFinishAction(
            expectedProgress = SlotPipelineDeploymentStatusProgress(
                ok = true,
                overridden = false,
                successCount = 1,
                totalCount = 1,
            )
        )
    }

    @Test
    fun `Deployment finish action OK when running and one errored workflow`() {
        forFinishAction(
            error = true,
            expectedProgress = SlotPipelineDeploymentStatusProgress(
                ok = false,
                overridden = false,
                successCount = 0,
                totalCount = 1,
            )
        )
    }

    @Test
    fun `Deployment finish action OK when running and one errored and overridden workflow`() {
        forFinishAction(
            error = true,
            overridden = true,
            expectedProgress = SlotPipelineDeploymentStatusProgress(
                ok = true,
                overridden = true,
                successCount = 1,
                totalCount = 1,
            )
        )
    }

    private fun forFinishAction(
        error: Boolean = false,
        overridden: Boolean = false,
        expectedProgress: SlotPipelineDeploymentStatusProgress,
    ) {
        slotWorkflowTestSupport.withSlotWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            error = error,
        ) { slot, slotWorkflow ->
            slot.project.branch {
                build {
                    val pipeline = slotService.startPipeline(slot, this)
                    val runStatus = slotService.runDeployment(pipeline.id, dryRun = false)
                    assertTrue(runStatus.ok, "Deployment running")
                    slotWorkflowTestSupport.waitForSlotWorkflowsToFinish(pipeline, SlotPipelineStatus.RUNNING)

                    if (error && overridden) {
                        val instance = slotWorkflowService.findSlotWorkflowInstanceByPipelineAndSlotWorkflow(
                            pipeline,
                            slotWorkflow,
                        ) ?: fail("Could not find slot workflow instance")
                        slotWorkflowService.overrideSlotWorkflowInstance(
                            slotWorkflowInstanceId = instance.id,
                            message = "Because this must pass"
                        )
                    }

                    run(
                        """
                            {
                                slotPipelineById(id: "${pipeline.id}") {
                                    finishAction {
                                        ok
                                        overridden
                                        successCount
                                        totalCount
                                        percentage
                                    }
                                }
                            }
                        """.trimIndent()
                    ) { data ->
                        assertEquals(
                            mapOf(
                                "slotPipelineById" to mapOf(
                                    "finishAction" to mapOf(
                                        "ok" to expectedProgress.ok,
                                        "overridden" to expectedProgress.overridden,
                                        "successCount" to expectedProgress.successCount,
                                        "totalCount" to expectedProgress.totalCount,
                                        "percentage" to expectedProgress.percentage,
                                    ),
                                )
                            ).asJson(),
                            data
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting a pipeline`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            run(
                """
                    mutation {
                        deleteDeployment(input: {deploymentId: "${pipeline.id}"}) {
                            errors {
                                message
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                checkGraphQLUserErrors(data, "deleteDeployment")
                assertNull(
                    slotService.findPipelineById(pipeline.id),
                    "Deployment has been deleted"
                )
            }
        }
    }

    @Test
    fun `Starting a pipeline in forced DONE status must not run the workflows`() {
        slotTestSupport.withSlot { slot ->
            // Registering workflows for each status
            slotWorkflowService.addSlotWorkflow(
                SlotWorkflow(
                    slot = slot,
                    trigger = SlotPipelineStatus.CANDIDATE,
                    workflow = WorkflowParser.parseYamlWorkflow(
                        """
                            name: On candidate
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Candidate
                        """.trimIndent()
                    )
                )
            )
            slotWorkflowService.addSlotWorkflow(
                SlotWorkflow(
                    slot = slot,
                    trigger = SlotPipelineStatus.RUNNING,
                    workflow = WorkflowParser.parseYamlWorkflow(
                        """
                            name: On running
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Running
                        """.trimIndent()
                    )
                )
            )
            slotWorkflowService.addSlotWorkflow(
                SlotWorkflow(
                    slot = slot,
                    trigger = SlotPipelineStatus.DONE,
                    workflow = WorkflowParser.parseYamlWorkflow(
                        """
                            name: On done
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Done
                        """.trimIndent()
                    )
                )
            )
            // Creating a pipeline in done mode
            slot.project.branch {
                build {
                    run(
                        """
                            mutation {
                                startSlotPipeline(input: {
                                    slotId: "${slot.id}",
                                    buildId: $id,
                                    forceDone: true,
                                    forceDoneMessage: "Direct done",
                                }) {
                                    pipeline {
                                        id
                                    }
                                    errors {
                                        message
                                    }
                                }
                            }
                        """.trimIndent()
                    ) { data ->
                        checkGraphQLUserErrors(data, "startSlotPipeline") { node ->
                            val id = node.path("pipeline")
                                .path("id").asText()
                            val pipeline = slotService.getPipelineById(id)
                            assertEquals(
                                SlotPipelineStatus.DONE,
                                pipeline.status,
                                "Pipeline done"
                            )
                            // Checks that no workflow in candidate or running has run
                            val instances = slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline)
                            assertEquals(
                                0,
                                instances.count { it.slotWorkflow.trigger == SlotPipelineStatus.CANDIDATE },
                                "No workflow on candidate"
                            )
                            assertEquals(
                                0,
                                instances.count { it.slotWorkflow.trigger == SlotPipelineStatus.RUNNING },
                                "No workflow on running"
                            )
                            assertEquals(
                                1,
                                instances.count { it.slotWorkflow.trigger == SlotPipelineStatus.DONE },
                                "1 workflow on done"
                            )
                            val change = slotService.getPipelineChanges(pipeline).firstOrNull()
                            assertNotNull(change) {
                                assertEquals(SlotPipelineStatus.DONE, it.status)
                                assertEquals("Direct done", it.message)
                                assertEquals(SlotPipelineChangeType.STATUS, it.type)
                                assertEquals("Direct done", it.overrideMessage)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Starting a pipeline in forced DONE status must not run the workflows included the DONE ones`() {
        slotTestSupport.withSlot { slot ->
            // Registering workflows for each status
            slotWorkflowService.addSlotWorkflow(
                SlotWorkflow(
                    slot = slot,
                    trigger = SlotPipelineStatus.CANDIDATE,
                    workflow = WorkflowParser.parseYamlWorkflow(
                        """
                            name: On candidate
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Candidate
                        """.trimIndent()
                    )
                )
            )
            slotWorkflowService.addSlotWorkflow(
                SlotWorkflow(
                    slot = slot,
                    trigger = SlotPipelineStatus.RUNNING,
                    workflow = WorkflowParser.parseYamlWorkflow(
                        """
                            name: On running
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Running
                        """.trimIndent()
                    )
                )
            )
            slotWorkflowService.addSlotWorkflow(
                SlotWorkflow(
                    slot = slot,
                    trigger = SlotPipelineStatus.DONE,
                    workflow = WorkflowParser.parseYamlWorkflow(
                        """
                            name: On done
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Done
                        """.trimIndent()
                    )
                )
            )
            // Creating a pipeline in done mode
            slot.project.branch {
                build {
                    run(
                        """
                            mutation {
                                startSlotPipeline(input: {
                                    slotId: "${slot.id}",
                                    buildId: $id,
                                    forceDone: true,
                                    forceDoneMessage: "Direct done",
                                    skipWorkflows: true,
                                }) {
                                    pipeline {
                                        id
                                    }
                                    errors {
                                        message
                                    }
                                }
                            }
                        """.trimIndent()
                    ) { data ->
                        checkGraphQLUserErrors(data, "startSlotPipeline") { node ->
                            val id = node.path("pipeline")
                                .path("id").asText()
                            val pipeline = slotService.getPipelineById(id)
                            assertEquals(
                                SlotPipelineStatus.DONE,
                                pipeline.status,
                                "Pipeline done"
                            )
                            // Checks that no workflow in candidate or running has run
                            val instances = slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline)
                            assertEquals(
                                0,
                                instances.count { it.slotWorkflow.trigger == SlotPipelineStatus.CANDIDATE },
                                "No workflow on candidate"
                            )
                            assertEquals(
                                0,
                                instances.count { it.slotWorkflow.trigger == SlotPipelineStatus.RUNNING },
                                "No workflow on running"
                            )
                            assertEquals(
                                0,
                                instances.count { it.slotWorkflow.trigger == SlotPipelineStatus.DONE },
                                "No workflow on done"
                            )
                            val change = slotService.getPipelineChanges(pipeline).firstOrNull()
                            assertNotNull(change) {
                                assertEquals(SlotPipelineStatus.DONE, it.status)
                                assertEquals("Direct done", it.message)
                                assertEquals(SlotPipelineChangeType.STATUS, it.type)
                                assertEquals("Direct done", it.overrideMessage)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `A failing workflow on a running deployment must mark it as errored`() {
        slotWorkflowTestSupport.withSlotWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            error = true,
        ) { slot, _ ->

            // Creating a pipeline
            val pipeline = slotTestSupport.createPipeline(slot = slot)

            // Launching the pipeline, this will create a failed workflow
            val status = slotService.runDeployment(pipeline.id, dryRun = false)
            assertTrue(status.ok, "Pipeline has started its deployment")

            // Waiting for the pipeline's workflows to finish
            slotWorkflowTestSupport.waitForSlotWorkflowsToFinish(pipeline, SlotPipelineStatus.RUNNING)

            // Getting the workflow status & error message
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            status
                            errorMessage
                        }
                    }
                """.trimIndent()
            ) { data ->

                val dataPipeline = data.path("slotPipelineById")
                assertEquals("RUNNING", dataPipeline.path("status").asText())
                assertEquals("Workflow is in error", dataPipeline.path("errorMessage").asText())
            }
        }
    }

    @Test
    fun `A successful workflow on a running deployment must not mark it as errored`() {
        slotWorkflowTestSupport.withSlotWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            error = false,
        ) { slot, _ ->

            // Creating a pipeline
            val pipeline = slotTestSupport.createPipeline(slot = slot)

            // Launching the pipeline, this will create a failed workflow
            val status = slotService.runDeployment(pipeline.id, dryRun = false)
            assertTrue(status.ok, "Pipeline has started its deployment")

            // Waiting for the pipeline's workflows to finish
            slotWorkflowTestSupport.waitForSlotWorkflowsToFinish(pipeline, SlotPipelineStatus.RUNNING)

            // Getting the workflow status & error message
            run(
                """
                    {
                        slotPipelineById(id: "${pipeline.id}") {
                            status
                            errorMessage
                        }
                    }
                """.trimIndent()
            ) { data ->
                val dataPipeline = data.path("slotPipelineById")
                assertEquals("RUNNING", dataPipeline.path("status").asText())
                assertJsonNull(dataPipeline.path("errorMessage"), "Workflow is not in error")
            }
        }
    }


    private fun pendingAndErrorOf(pipeline: SlotPipeline, code: (pipelineNode: tools.jackson.databind.JsonNode) -> Unit) {
        run(
            """
                {
                    slotPipelineById(id: "${pipeline.id}") {
                        status
                        errorMessage
                        pendingMessage
                        slot {
                            blocked
                            blockingState
                        }
                        admissionRules {
                            check {
                                ok
                                state
                                reason
                            }
                        }
                        slotWorkflowInstances {
                            check {
                                ok
                                state
                                reason
                            }
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            code(data.path("slotPipelineById"))
        }
    }

    @Test
    fun `A running workflow on a running deployment is pending, not an error`() {
        slotWorkflowTestSupport.withSlotWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            waitMs = 5_000,
        ) { slot, _ ->
            val pipeline = slotTestSupport.createPipeline(slot = slot)
            val status = slotService.runDeployment(pipeline.id, dryRun = false)
            assertTrue(status.ok, "Pipeline has started its deployment")

            // While the workflow runs
            pendingAndErrorOf(pipeline) { node ->
                assertEquals("RUNNING", node.path("status").asText())
                assertJsonNull(node.path("errorMessage"), "A running workflow is not an error")
                assertTrue(
                    node.path("pendingMessage").asText() in setOf("Workflow has started", "Workflow is running"),
                    "The running workflow is what the deployment is waiting for"
                )
                assertEquals(true, node.path("slot").path("blocked").asBoolean())
                assertEquals("PENDING", node.path("slot").path("blockingState").asText())
                val check = node.path("slotWorkflowInstances").single().path("check")
                assertEquals(false, check.path("ok").asBoolean())
                assertEquals("PENDING", check.path("state").asText())
            }

            // Once the workflow is done
            slotWorkflowTestSupport.waitForSlotWorkflowsToSucceed(pipeline, SlotPipelineStatus.RUNNING)
            pendingAndErrorOf(pipeline) { node ->
                assertJsonNull(node.path("errorMessage"))
                assertJsonNull(node.path("pendingMessage"), "Nothing is pending any longer")
                assertEquals(false, node.path("slot").path("blocked").asBoolean())
                assertEquals("OK", node.path("slot").path("blockingState").asText())
                assertEquals("OK", node.path("slotWorkflowInstances").single().path("check").path("state").asText())
            }
        }
    }

    @Test
    fun `A workflow in error on a running deployment has failed`() {
        slotWorkflowTestSupport.withSlotWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            error = true,
        ) { slot, _ ->
            val pipeline = slotTestSupport.createPipeline(slot = slot)
            slotService.runDeployment(pipeline.id, dryRun = false)
            slotWorkflowTestSupport.waitForSlotWorkflowsToFinish(pipeline, SlotPipelineStatus.RUNNING)
            pendingAndErrorOf(pipeline) { node ->
                assertEquals("Workflow is in error", node.path("errorMessage").asText())
                assertJsonNull(node.path("pendingMessage"))
                assertEquals(true, node.path("slot").path("blocked").asBoolean())
                assertEquals("FAILED", node.path("slot").path("blockingState").asText())
                assertEquals(
                    "FAILED",
                    node.path("slotWorkflowInstances").single().path("check").path("state").asText()
                )
            }
        }
    }

    @Test
    fun `A candidate waiting for a manual approval is pending, and failed once rejected`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val config = SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(pipeline.slot)
            slotService.addAdmissionRuleConfig(config)

            // Waiting for the approval
            pendingAndErrorOf(pipeline) { node ->
                assertEquals("CANDIDATE", node.path("status").asText())
                assertJsonNull(node.path("errorMessage"), "Waiting for an approval is not an error")
                assertEquals("No approval", node.path("pendingMessage").asText())
                assertEquals("PENDING", node.path("slot").path("blockingState").asText())
                val check = node.path("admissionRules").single().path("check")
                assertEquals(false, check.path("ok").asBoolean())
                assertEquals("PENDING", check.path("state").asText())
            }

            // Rejecting
            slotService.setupAdmissionRule(
                pipeline = pipeline,
                admissionRuleConfig = config,
                data = ManualApprovalSlotAdmissionRuleData(approval = false, message = "No way").asJson(),
            )
            pendingAndErrorOf(pipeline) { node ->
                assertEquals("Rejected", node.path("errorMessage").asText())
                assertJsonNull(node.path("pendingMessage"))
                assertEquals("FAILED", node.path("slot").path("blockingState").asText())
                assertEquals("FAILED", node.path("admissionRules").single().path("check").path("state").asText())
            }
        }
    }

}