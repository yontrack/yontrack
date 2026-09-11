package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@AsAdminTest
class GQLBuildSlotPipelinesFieldContributorIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    /**
     * One build deployed twice into the *same* environment: once into the project's unqualified
     * slot, once into its `demo` slot. Two slots of one project in one environment are told apart
     * only by their qualifier, which is exactly the case `currentDeployments` used to be blind to.
     */
    private fun withBuildDeployedInTwoQualifiers(
        code: (build: Build, defaultDeployment: SlotPipeline, qualifiedDeployment: SlotPipeline) -> Unit,
    ) {
        project {
            environmentTestSupport.withEnvironment { environment ->
                slotTestSupport.withSlot(
                    project = project,
                    environment = environment,
                ) { defaultSlot ->
                    slotTestSupport.withSlot(
                        project = project,
                        environment = environment,
                        qualifier = "demo",
                    ) { qualifiedSlot ->
                        branch {
                            build {
                                val defaultDeployment = slotService.startPipeline(defaultSlot, this)
                                slotTestSupport.runAndFinishDeployment(defaultDeployment)
                                val qualifiedDeployment = slotService.startPipeline(qualifiedSlot, this)
                                slotTestSupport.runAndFinishDeployment(qualifiedDeployment)
                                code(this, defaultDeployment, qualifiedDeployment)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun currentDeployments(build: Build, argument: String = ""): Set<String> {
        var ids = emptySet<String>()
        run(
            """
                {
                    build(id: ${build.id}) {
                        currentDeployments$argument {
                            id
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            ids = data.path("build").path("currentDeployments")
                .map { it.path("id").asText() }
                .toSet()
        }
        return ids
    }

    @Test
    fun `Current deployments with no qualifier answer with every qualifier`() {
        withBuildDeployedInTwoQualifiers { build, defaultDeployment, qualifiedDeployment ->
            assertEquals(
                setOf(defaultDeployment.id, qualifiedDeployment.id),
                currentDeployments(build),
            )
        }
    }

    @Test
    fun `Current deployments filtered on a qualifier`() {
        withBuildDeployedInTwoQualifiers { build, _, qualifiedDeployment ->
            assertEquals(
                setOf(qualifiedDeployment.id),
                currentDeployments(build, """(qualifier: "demo")"""),
            )
        }
    }

    @Test
    fun `Current deployments filtered on the default qualifier`() {
        withBuildDeployedInTwoQualifiers { build, defaultDeployment, _ ->
            assertEquals(
                setOf(defaultDeployment.id),
                currentDeployments(build, """(qualifier: "")"""),
            )
        }
    }

    @Test
    fun `Current deployments leave out a deployment superseded by a newer build`() {
        slotTestSupport.withSlot(qualifier = "demo") { slot ->
            val old = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            val new = slotTestSupport.createRunAndFinishDeployment(slot = slot)
            assertEquals(
                emptySet(),
                currentDeployments(old.build),
            )
            assertEquals(
                setOf(new.id),
                currentDeployments(new.build),
            )
        }
    }

}
