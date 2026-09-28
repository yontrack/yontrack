package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.security.EnvironmentList
import net.nemerosa.ontrack.extension.environments.security.SlotPipelineData
import net.nemerosa.ontrack.extension.environments.security.SlotView
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertTrue

@Component
class SlotTestSupport : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    fun slot(
        order: Int = 1,
        environment: Environment? = null,
        project: Project = project(),
        qualifier: String = Slot.DEFAULT_QUALIFIER,
    ): Slot =
        asAdmin {
            val env = environment ?: environmentTestSupport.withEnvironment(order = order) {}
            val slot = SlotTestFixtures.testSlot(
                env = env,
                project = project,
                qualifier = qualifier,
            )
            slotService.addSlot(slot)
            slot
        }

    fun withSlot(
        order: Int = 1,
        environment: Environment? = null,
        project: Project = project(),
        qualifier: String = Slot.DEFAULT_QUALIFIER,
        code: (slot: Slot) -> Unit,
    ): Slot =
        slot(
            order, environment, project, qualifier
        ).apply {
            asAdmin {
                code(this)
            }
        }

    fun withSlotPipeline(
        branchName: String = uid("B"),
        qualifier: String = Slot.DEFAULT_QUALIFIER,
        code: (pipeline: SlotPipeline) -> Unit,
    ) = withSlot(qualifier = qualifier) { slot ->
        slot.project.branch(name = branchName) {
            build {
                val pipeline = slotService.startPipeline(slot, this)
                code(pipeline)
            }
        }
    }

    fun createPipeline(
        branchName: String = uid("B"),
        slot: Slot
    ): SlotPipeline {
        val branch = structureService.findBranchByName(slot.project.name, branchName)
            .getOrNull()
            ?: slot.project.branch(branchName)
        val build = branch.build()
        return slotService.startPipeline(slot, build)
    }

    fun createRunAndFinishDeployment(
        branchName: String = uid("B"),
        slot: Slot
    ): SlotPipeline =
        createPipeline(branchName, slot).apply {
            runAndFinishDeployment(this)
        }

    /**
     * Runs and finishes the deployment of a pipeline.
     *
     * @param dateTime When not null, both the run and the finish are backdated to this time
     */
    fun runAndFinishDeployment(pipeline: SlotPipeline, dateTime: LocalDateTime? = null) {
        val status = slotService.runDeployment(pipeline.id, dryRun = false, dateTime = dateTime)
        assertTrue(status.ok, "Pipeline deploying")
        val result = slotService.finishDeployment(pipeline.id, dateTime = dateTime)
        assertTrue(result.ok, "Pipeline deployed")
    }

    fun withRunningDeployment(
        branchName: String = uid("B"),
        code: (pipeline: SlotPipeline) -> Unit,
    ) {
        withSlotPipeline(branchName = branchName) { pipeline ->
            val status = slotService.runDeployment(pipeline.id, dryRun = false)
            assertTrue(status.ok, "Pipeline running")
            code(pipeline)
        }
    }

    fun withFinishedDeployment(
        branchName: String = uid("B"),
        code: (pipeline: SlotPipeline) -> Unit,
    ) {
        withSlotPipeline(branchName = branchName) { pipeline ->
            runAndFinishDeployment(pipeline)
            code(pipeline)
        }
    }

    fun withSlotUser(
        slot: Slot,
        email: String = uid("u-") + "@ontrack.local",
        code: (user: ConfigurableAccountCall) -> Unit,
    ) {
        val user = asUser(email = email)
            .with(EnvironmentList::class.java)
            .withProjectFunction(slot.project, SlotView::class.java)
            .withProjectFunction(slot.project, ProjectView::class.java)
            .withProjectFunction(slot.project, SlotPipelineData::class.java)
        user.call {
            code(user)
        }
    }

    fun withSquareSlotsAndOther(
        code: (
            project: Project,
            stagingDefaultSlot: Slot,
            stagingDemoSlot: Slot,
            productionDefaultSlot: Slot,
            productionDemoSlot: Slot,
            other: Slot
        ) -> Unit
    ) {
        project {
            environmentTestSupport.withEnvironment { staging ->
                withSlot(
                    project = project,
                    environment = staging
                ) { stagingDefaultSlot ->
                    withSlot(
                        project = project,
                        environment = staging,
                        qualifier = "demo"
                    ) { stagingDemoSlot ->
                        environmentTestSupport.withEnvironment { production ->
                            withSlot(
                                project = project,
                                environment = production
                            ) { productionDefaultSlot ->
                                withSlot(
                                    project = project,
                                    environment = production,
                                    qualifier = "demo"
                                ) { productionDemoSlot ->

                                    // Only release branches for the production default slot
                                    slotService.addAdmissionRuleConfig(
                                        config = SlotAdmissionRuleTestFixtures.testBranchPatternAdmissionRuleConfig(
                                            productionDefaultSlot
                                        )
                                    )

                                    // Whole project altogether
                                    withSlot { other ->

                                        // Running the code
                                        code(
                                            project,
                                            stagingDefaultSlot,
                                            stagingDemoSlot,
                                            productionDefaultSlot,
                                            productionDemoSlot,
                                            other,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}