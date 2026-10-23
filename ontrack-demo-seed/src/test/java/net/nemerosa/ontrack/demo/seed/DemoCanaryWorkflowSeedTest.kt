package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The CANARY promotion workflow of the demo (#2114): a workflow made of real nodes - a deployment
 * to the staging canary, two Jenkins jobs run in SYNC mode on the mock Jenkins, and a promotion -
 * which passes on `main` and fails at its performance gate on the maintenance branch.
 *
 * The in-memory target runs the workflow the way the server would, node by node, so what is pinned
 * here is the demo the workflows leave behind, and the order the seed starts them in.
 */
class DemoCanaryWorkflowSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { seed(it).run(DemoContent.dataset(emptyList())) }

    private fun InMemoryDemoTarget.build(branch: String, name: String): InMemoryDemoTarget.InMemoryBuild =
        projects()
            .map { it as InMemoryDemoTarget.InMemoryProject }
            .single { it.name == DemoContent.SERVICE }
            .branches.single { it.name == branch }
            .builds.single { it.name == name }

    private fun InMemoryDemoTarget.slot(environment: String, qualifier: String): InMemoryDemoTarget.InMemorySlot =
        environments()
            .flatMap { (it as InMemoryDemoTarget.InMemoryEnvironment).slots }
            .single {
                it.environment.name == environment && it.project.name == DemoContent.SERVICE && it.qualifier == qualifier
            }

    @Test
    fun `the canary workflow of main deploys the head of main to the staging canary and makes it releasable`() {
        val target = seeded()
        val build = target.build(DemoContent.MAIN, "107")

        val run = build.workflowRuns.getValue(DemoContent.CANARY)
        assertEquals(WorkflowOutcome.SUCCESS, run.outcome, "Errors: ${run.errors}")
        assertTrue(
            build.promotions.any { it.first == DemoContent.RELEASABLE },
            "The workflow's last node promotes the build",
        )
        assertEquals("107", target.slot(DemoContent.STAGING, DemoContent.CANARY_QUALIFIER).heldBuilds.last().name)
    }

    @Test
    fun `the canary workflow of the maintenance branch stops at the performance gate`() {
        val build = seeded().build(DemoContent.MAINTENANCE, "89")

        val run = build.workflowRuns.getValue(DemoContent.CANARY)
        assertEquals(WorkflowOutcome.ERROR, run.outcome)
        assertTrue(run.errors.any { "performance-gate" in it }, "The gate is what failed: ${run.errors}")
        assertFalse(
            build.promotions.any { it.first == DemoContent.RELEASABLE },
            "The promotion node never runs past a failed gate",
        )
    }

    @Test
    fun `the staging canary is deployed by the workflows, the maintenance build first`() {
        val slot = seeded().slot(DemoContent.STAGING, DemoContent.CANARY_QUALIFIER)

        assertEquals(
            listOf("89", "107"),
            slot.deployments.map { it.build.name },
            "Oldest build first, so that the slot ends up holding the head of main",
        )
        assertTrue(slot.deployments.all { it.stopAt == DeploymentStop.DONE })
    }

    @Test
    fun `nothing grants RELEASABLE but the workflow`() {
        DemoContent.dataset(emptyList()).projects.flatMap { it.branches }.flatMap { it.builds }.forEach { build ->
            assertFalse(
                DemoContent.RELEASABLE in build.promotionLevels,
                "Build ${build.name} is promoted to ${DemoContent.RELEASABLE} by the dataset",
            )
        }
    }

    @Test
    fun `the canary workflows run once the deployments are done, and the production canary candidate after them`() {
        val target = InMemoryDemoTarget()
        seed(target).run(DemoContent.dataset(emptyList()))

        val journal = target.journal
        val workflows = journal.indices.filter { journal[it].startsWith("workflow ") }
        assertEquals(2, workflows.size, "One workflow per CANARY promotion: $journal")
        val lastDeploymentBefore = journal.indexOfLast { it == "deploy staging/${DemoContent.SERVICE} 107 RUNNING" }
        assertTrue(lastDeploymentBefore in 0 until workflows.first(), "The declared deployments come first: $journal")
        val candidate = journal.indexOf("deploy production/${DemoContent.SERVICE} [canary] 104 CANDIDATE")
        assertTrue(candidate > workflows.last(), "The production canary candidate reads what the workflows deployed: $journal")
    }

    @Test
    fun `a workflow needing what the instance does not run is refused before anything is deleted`() {
        val target = InMemoryDemoTarget()
        seed(target).run(DemoContent.dataset(emptyList()))
        val before = target.snapshot()

        val error = assertFailsWith<IllegalStateException> {
            seed(LackingWorkflows(target, setOf("mock-jenkins"))).run(DemoContent.dataset(emptyList()))
        }
        assertTrue("mock-jenkins" in error.message.orEmpty(), error.message)
        assertTrue("Nothing was deleted" in error.message.orEmpty(), error.message)
        assertEquals(before, target.snapshot(), "The demo is untouched")
    }

    @Test
    fun `a workflow ending otherwise than the dataset expects stops the seed`() {
        val error = assertFailsWith<IllegalStateException> {
            seed(InMemoryDemoTarget()).run(
                miniDataset(
                    workflow = WorkflowSpec(deployAndPromote(), expected = WorkflowOutcome.SUCCESS),
                    // Nothing the build can satisfy: the deployment node fails
                    slotRule = SlotAdmissionRuleSpec("gold", SlotAdmissionRules.PROMOTION, mapOf("promotion" to "RELEASABLE")),
                )
            )
        }
        assertTrue("CANARY" in error.message.orEmpty() && "ERROR" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the promotions after a level carrying a workflow are made once the workflow has finished`() {
        val build = seeded().build(DemoContent.MAIN, "107")

        assertEquals(
            listOf(DemoContent.BRONZE, DemoContent.SILVER, DemoContent.CANARY, DemoContent.RELEASABLE, DemoContent.GOLD),
            build.promotions.map { it.first },
            "GOLD comes after what the canary verification granted",
        )
    }

    @Test
    fun `a build promoted by the dataset to a level a workflow grants is caught before anything is deleted`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                miniDataset(promotionLevels = listOf("RELEASABLE", "CANARY"))
            )
        }
        assertTrue("RELEASABLE" in error.message.orEmpty() && "workflow" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a workflow deploying to a slot the dataset never creates is caught before anything is deleted`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                miniDataset(workflow = WorkflowSpec(deployAndPromote(qualifier = "elsewhere")))
            )
        }
        assertTrue("staging" in error.message.orEmpty() && "elsewhere" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a workflow promoting to a level the branch does not declare is caught before anything is deleted`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                miniDataset(workflow = WorkflowSpec(deployAndPromote(promotion = "PLATINUM")))
            )
        }
        assertTrue("PLATINUM" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a workflow node without data is caught before anything is deleted`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                miniDataset(workflow = WorkflowSpec(deployAndPromote().replace(Regex("""\n\s*data: \{}"""), "")))
            )
        }
        assertTrue("deploying" in error.message.orEmpty() && "without data" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the nodes of a workflow are read off its definition`() {
        val spec = WorkflowSpec(deployAndPromote())

        assertEquals(
            setOf("slot-pipeline-creation", "slot-pipeline-deploying", "slot-pipeline-deployed", "notification"),
            spec.executorIds,
        )
        assertEquals(setOf("mock-jenkins", "yontrack-promotion"), spec.notificationChannels)
        assertEquals(setOf("RELEASABLE"), spec.promotions)
        assertEquals(setOf("staging" to "canary"), spec.deploymentSlots)
    }

    /**
     * A target which does not run some of the workflow node executors or notification channels,
     * keeping what [delegate] holds - so that what the refused run would have deleted can be checked.
     */
    private class LackingWorkflows(
        private val delegate: InMemoryDemoTarget,
        private val missing: Set<String>,
    ) : DemoTarget by delegate {
        override fun checkWorkflowsAvailable(executorIds: Set<String>, notificationChannels: Set<String>) {
            InMemoryDemoTarget(missingWorkflowParts = missing).checkWorkflowsAvailable(executorIds, notificationChannels)
        }
    }

    private fun deployAndPromote(
        qualifier: String = "canary",
        promotion: String = "RELEASABLE",
    ) = """
        name: Canary
        nodes:
          - id: create
            executorId: slot-pipeline-creation
            data: { environment: staging, qualifier: $qualifier }
          - id: deploying
            parents: [{id: create}]
            executorId: slot-pipeline-deploying
            data: {}
          - id: deployed
            parents: [{id: deploying}]
            executorId: slot-pipeline-deployed
            data: {}
          - id: gate
            parents: [{id: deployed}]
            executorId: notification
            data:
              channel: mock-jenkins
              channelConfig: { config: demo, job: gate, callMode: SYNC }
          - id: promote
            parents: [{id: gate}]
            executorId: notification
            data:
              channel: yontrack-promotion
              channelConfig: { promotion: $promotion }
    """.trimIndent()

    private fun miniDataset(
        workflow: WorkflowSpec = WorkflowSpec(deployAndPromote()),
        promotionLevels: List<String> = listOf("CANARY"),
        slotRule: SlotAdmissionRuleSpec? = null,
    ) = DemoDataset(
        projects = listOf(
            ProjectSpec(
                name = "one",
                description = "",
                branches = listOf(
                    BranchSpec(
                        name = "main",
                        description = "",
                        promotionLevels = listOf(
                            PromotionLevelSpec("CANARY", "", workflow = workflow),
                            PromotionLevelSpec("RELEASABLE", ""),
                            PromotionLevelSpec("GOLD", ""),
                        ),
                        builds = listOf(
                            BuildSpec(
                                name = "1",
                                description = "",
                                creation = BuildCreation.DaysAgo(1),
                                promotionLevels = promotionLevels,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        environments = listOf(
            EnvironmentSpec(
                name = "staging",
                order = 100,
                description = "",
                slots = listOf(
                    SlotSpec(
                        project = "one",
                        qualifier = "canary",
                        description = "",
                        admissionRules = listOfNotNull(slotRule),
                    ),
                ),
            ),
        ),
    )
}
