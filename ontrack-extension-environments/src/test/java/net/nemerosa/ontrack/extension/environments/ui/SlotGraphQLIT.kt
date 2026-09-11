package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

@AsAdminTest
class SlotGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Test
    fun `Creating a list of slots`() {
        asAdmin {
            environmentTestSupport.withEnvironment { env1 ->
                environmentTestSupport.withEnvironment { env2 ->
                    project {
                        run(
                            """
                                mutation CreateSlots(${'$'}environmentIds: [String!]!) {
                                    createSlots(input: {
                                        projectId: ${project.id},
                                        environmentIds: ${'$'}environmentIds,
                                    }) {
                                        errors {
                                            message
                                        }
                                    }
                                }
                            """,
                            mapOf(
                                "environmentIds" to listOf(env1.id, env2.id)
                            )
                        ) { data ->
                            checkGraphQLUserErrors(data, "createSlots")
                            assertEquals(
                                listOf(env1 to project),
                                slotService.findSlotsByEnvironment(env1).map {
                                    it.environment to it.project
                                }
                            )
                            assertEquals(
                                listOf(env2 to project),
                                slotService.findSlotsByEnvironment(env2).map {
                                    it.environment to it.project
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Getting slots for an environment`() {
        asAdmin {
            slotTestSupport.withSlot { slot ->
                run(
                    """
                {
                    environmentByName(name: "${slot.environment.name}") {
                        id
                        slots {
                            id
                        }
                    }
                }
            """
                ) { data ->
                    val environment = data.path("environmentByName")
                    val environmentId = environment.path("id").asText()
                    assertEquals(slot.environment.id, environmentId)
                    assertEquals(
                        listOf(slot.id),
                        environment.path("slots").map {
                            it.path("id").asText()
                        }
                    )
                }
            }
        }
    }

    @Test
    fun `Last eligible for a slot can be empty`() {
        asAdmin {
            slotTestSupport.withSlot { slot ->
                slot.project.branch {
                    run(
                        """
                        {
                            slotById(id: "${slot.id}") {
                                eligibleBuild {
                                    id
                                }
                            }
                        }
                    """.trimIndent()
                    ) { data ->
                        val slotData = data.path("slotById")
                        val build = slotData.path("eligibleBuild")
                        assertJsonNull(build, "No eligible build")
                    }
                }
            }
        }
    }

    @Test
    fun `Last eligible for a slot`() {
        asAdmin {
            slotTestSupport.withSlot { slot ->
                slot.project.branch {
                    val build = build()
                    run(
                        """
                        {
                            slotById(id: "${slot.id}") {
                                eligibleBuild {
                                    id
                                }
                            }
                        }
                    """.trimIndent()
                    ) { data ->
                        val slotData = data.path("slotById")
                        val buildData = slotData.path("eligibleBuild")
                        assertEquals(
                            build.id(),
                            buildData.path("id").asInt()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Getting a list of eligible slots for a build`() {
        slotTestSupport.withSquareSlotsAndOther { project, stagingDefaultSlot, stagingDemoSlot, productionDefaultSlot, productionDemoSlot, _ ->
            // Creating a build on a non-release branch
            project.branch {
                build {
                    // Checking its eligible slots
                    run(
                        """{
                            eligibleSlotsForBuild(buildId: $id) {
                                eligible
                                slot {
                                    id
                                }
                            }
                        }""".trimIndent()
                    ) { data ->
                        val eligibleSlots = data.path("eligibleSlotsForBuild")
                        val index = eligibleSlots.associate {
                            it.path("slot").path("id").asText() to it.path("eligible")
                                .asBoolean()
                        }
                        assertEquals(
                            mapOf(
                                stagingDefaultSlot.id to true,
                                stagingDemoSlot.id to true,
                                productionDefaultSlot.id to false,
                                productionDemoSlot.id to true,
                            ),
                            index
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A non eligible slot says which of its admission rules refuses the build`() {
        // A list of environments a build cannot go to, with no reason beside them, leaves a user
        // wondering where an environment went. `eligible` alone cannot answer that: the slot
        // refuses the build because one of its admission rules does, and only the server knows
        // which one.
        slotTestSupport.withSquareSlotsAndOther { project, stagingDefaultSlot, _, productionDefaultSlot, _, _ ->
            project.branch {
                build {
                    run(
                        """{
                            eligibleSlotsForBuild(buildId: $id) {
                                eligible
                                nonEligibleRules {
                                    name
                                    ruleId
                                }
                                slot {
                                    id
                                }
                            }
                        }""".trimIndent()
                    ) { data ->
                        val index = data.path("eligibleSlotsForBuild").associateBy {
                            it.path("slot").path("id").asText()
                        }

                        // The production default slot admits release branches only, and this build
                        // is not on one.
                        val refused = index.getValue(productionDefaultSlot.id)
                        assertEquals(false, refused.path("eligible").asBoolean())
                        assertEquals(
                            listOf("releaseBranchesOnly" to "branchPattern"),
                            refused.path("nonEligibleRules").map {
                                it.path("name").asText() to it.path("ruleId").asText()
                            },
                            "The rule which refuses the build is named, not merely counted",
                        )

                        // An eligible slot has nothing to explain.
                        val admitted = index.getValue(stagingDefaultSlot.id)
                        assertEquals(true, admitted.path("eligible").asBoolean())
                        assertEquals(0, admitted.path("nonEligibleRules").size())
                    }
                }
            }
        }
    }

    @Test
    fun `Getting a list of pipelines for a build`() {
        slotTestSupport.withSlotPipeline { _ ->
            slotTestSupport.withSlotPipeline { pipeline1 ->
                slotTestSupport.withSlot(project = pipeline1.slot.project) { slot2 ->
                    val pipeline2 = slotService.startPipeline(slot2, pipeline1.build)
                    run(
                        """{
                            build(id: ${pipeline1.build.id}) {
                                slotPipelines {
                                    id
                                }
                            }
                        }""".trimIndent()
                    ) { data ->
                        val pipelineIds = data.path("build")
                            .path("slotPipelines")
                            .map { it.path("id").asText() }
                        assertEquals(
                            listOf(
                                pipeline2.id,
                                pipeline1.id,
                            ),
                            pipelineIds
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Getting list of eligible builds for a slot`() {
        slotTestSupport.withSlot { slot ->

            val main = slot.project.branch(name = "main")
            val silver = main.promotionLevel("SILVER")

            val otherBranch = slot.project.branch(name = "other")

            val build1 = main.build {
                promote(silver)
            }
            val build2 = main.build()
            /* val build3 = */ otherBranch.build()

            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(
                    slot = slot,
                    promotion = silver.name,
                )
            )

            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            eligibleBuilds {
                                pageItems {
                                    id
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val builds = data.path("slotById")
                    .path("eligibleBuilds")
                    .path("pageItems")
                assertEquals(
                    listOf(build2.id(), build1.id()),
                    builds.map {
                        it.path("id").asInt()
                    }
                )
            }

            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            eligibleBuilds(deployable: true) {
                                pageItems {
                                    id
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val builds = data.path("slotById")
                    .path("eligibleBuilds")
                    .path("pageItems")
                assertEquals(
                    listOf(build1.id()),
                    builds.map {
                        it.path("id").asInt()
                    }
                )
            }
        }
    }

    @Test
    fun `Getting list of eligible slots for a build`() {
        asAdmin {
            val project = project()
            val build = project.branch<Build> {
                build()
            }
            val slot1 = slotTestSupport.withSlot(project = project) {
                // Eligible
            }
            val slot2 = slotTestSupport.withSlot(project = project) {
                // Eligible
            }
            /* val slot3 = */ slotTestSupport.withSlot(project = project) {
            // Not eligible
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(it)
            )
        }

            run(
                """{
                    build(id: ${build.id}) {
                        eligibleSlots {
                            id
                        }
                    }
                }""".trimIndent()
            ) { data ->
                assertEquals(
                    setOf(
                        slot1.id,
                        slot2.id,
                    ),
                    data.path("build")
                        .path("eligibleSlots")
                        .map { it.path("id").asText() }
                        .toSet()
                )
            }
        }
    }

    @Test
    fun `Deleting a slot`() {
        slotTestSupport.withSlot { slot ->
            run(
                """
                    mutation {
                        deleteSlot(input: {slotId: "${slot.id}"}) {
                            errors {
                                message
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "deleteSlot")
                assertNull(
                    slotService.findSlotsByEnvironment(slot.environment).find { it.id == slot.id },
                    "Slot has been deleted"
                )
            }
        }
    }

    @Test
    fun `List of deployments for a slot filtered by build`() {
        slotTestSupport.withSlot { slot ->
            slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val deployment = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            pipelines(buildId: ${deployment.build.id}) {
                                pageItems {
                                    id
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val deploymentId = data.path("slotById").path("pipelines")
                    .path("pageItems").single()
                    .path("id").asText()
                assertEquals(
                    deployment.id,
                    deploymentId
                )
            }
        }
    }

    @Test
    fun `List of deployments filtered by branch name`() {
        slotTestSupport.withSlot { slot ->
            val pipeline121 = slotTestSupport.createPipeline(
                branchName = "release-12",
                slot = slot,
            )
            val pipeline122 = slotTestSupport.createPipeline(
                branchName = "release-12",
                slot = slot,
            )
            /* val pipeline13_2 = */ slotTestSupport.createPipeline(
            branchName = "release-13",
            slot = slot,
        )
            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            pipelines(branchName: "release-12") {
                                pageItems {
                                    id
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val deploymentIds = data.path("slotById").path("pipelines")
                    .path("pageItems")
                    .map { it.path("id").asText() }
                assertEquals(
                    listOf(pipeline122.id, pipeline121.id),
                    deploymentIds
                )
            }
        }
    }

    @Test
    fun `List of deployments filtered by status`() {
        slotTestSupport.withSlot { slot ->
            val firstPipelineFinished = slotTestSupport.createRunAndFinishDeployment(
                slot = slot,
            )
            /* val pipelineStarted = */ slotTestSupport.createPipeline(
                slot = slot,
            )
            /* val pipelineRunning = */ slotTestSupport.createPipeline(slot = slot).apply {
                slotService.runDeployment(this.id, dryRun = false)
            }
            val pipelineFinished = slotTestSupport.createRunAndFinishDeployment(
                slot = slot,
            )
            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            pipelines(done: true) {
                                pageItems {
                                    id
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                val deploymentIds = data.path("slotById").path("pipelines")
                    .path("pageItems")
                    .map { it.path("id").asText() }
                assertEquals(
                    listOf(pipelineFinished.id, firstPipelineFinished.id),
                    deploymentIds
                )
            }
        }
    }

}