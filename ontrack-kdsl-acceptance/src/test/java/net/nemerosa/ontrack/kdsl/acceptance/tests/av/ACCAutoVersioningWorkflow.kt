package net.nemerosa.ontrack.kdsl.acceptance.tests.av

import net.nemerosa.ontrack.kdsl.acceptance.tests.scm.withMockScmRepository
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineStatus
import net.nemerosa.ontrack.kdsl.spec.extension.av.autoVersioning
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.extension.environments.workflows.addWorkflow
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ACCAutoVersioningWorkflow : AbstractACCAutoVersioningTestSupport() {

    @Test
    fun `Deployment workflow triggering an auto-versioning followed by the change of state to deployed`() {
        // Creating an application project
        val application = project { this }
        val applicationBranch = application.branch { this }

        // Creating an environment
        val environment = ontrack.environments.createEnvironment(
            name = uid("env-"),
            order = 0,
        )
        // Creating a slot for the environment & the project
        val slot = environment.createSlot(
            project = application,
        )

        // Creating a GitOps project targeted by the deployment
        withMockScmRepository(ontrack) {
            withAutoVersioning {
                repositoryFile("gradle.properties") { "version = 0.0.1" }

                project {
                    val gitOps = this
                    branch("main") {
                        val gitOpsBranch = this
                        configuredForMockRepository()

                        // Adding a workflow to this slot
                        slot.addWorkflow(
                            trigger = SlotPipelineStatus.RUNNING,
                            workflowYaml = """
                                name: Deployment
                                nodes:
                                  - id: av
                                    executorId: auto-versioning
                                    data:
                                        targetProject: ${gitOps.name}
                                        targetBranch: ${gitOpsBranch.name}
                                        targetPath: gradle.properties
                                        targetProperty: version
                                        targetVersion: ${'$'}{build}
                                  - id: deployed
                                    parents:
                                      - id: av
                                    executorId: slot-pipeline-deployed
                                    data: {}
                            """.trimIndent()
                        )

                        // Creating a build for the application project
                        val build = applicationBranch.build(name = "1.0.0") { this }
                        // Creating a pipeline for this build & starting its deployment
                        val pipeline = slot.createPipeline(build = build)
                        pipeline.startDeploying()

                        // We expect the pipeline to become deployed
                        waitUntil(
                            task = "Pipeline deployed",
                            timeout = 10_000,
                            interval = 1_000,
                        ) {
                            ontrack.environments.findPipelineById(pipeline.id)?.status == SlotPipelineStatus.DONE
                        }

                        // We expect the GitOps repository to contain the new version
                        assertThatMockScmRepository {
                            fileContains("gradle.properties") { "version = 1.0.0" }
                        }

                        // We expect an entry in the AV audit trail
                        val entry = ontrack.autoVersioning.audit.entries(
                            project = gitOps.name
                        ).firstOrNull()
                        assertNotNull(entry, "Audit entry has been created")
                    }
                }
            }
        }
    }


    /**
     * The version rule of an `auto-versioning` node is a safety net. Saving a deployment workflow whose
     * rule cannot be resolved has to fail here, not at the deployment the rule was meant to guard.
     */
    @Test
    fun `A deployment workflow naming an unknown version rule is rejected when it is saved`() {
        val ex = assertFailsWith<GraphQLClientException> {
            addAutoVersioningWorkflow(versionRule = "no-such-rule")
        }
        assertTrue(
            "no-such-rule" in (ex.message ?: ""),
            "The unknown rule is named in the error: ${ex.message}"
        )
    }

    @Test
    fun `A deployment workflow whose version rule configuration cannot be parsed is rejected when it is saved`() {
        val ex = assertFailsWith<GraphQLClientException> {
            addAutoVersioningWorkflow(
                versionRule = "semver",
                versionRuleConfig = "{onUnparseable: NO_SUCH_POLICY}",
            )
        }
        assertTrue(
            "semver" in (ex.message ?: ""),
            "The rule whose configuration is not valid is named in the error: ${ex.message}"
        )
    }

    @Test
    fun `A deployment workflow naming a known version rule is saved`() {
        addAutoVersioningWorkflow(versionRule = "semver")
    }

    /**
     * Registers a one-node deployment workflow on a fresh slot. The auto-versioning target does not need
     * to exist: the save-time validation looks at the version rule only.
     */
    private fun addAutoVersioningWorkflow(
        versionRule: String,
        versionRuleConfig: String = "{}",
    ) {
        val application = project { this }
        val environment = ontrack.environments.createEnvironment(
            name = uid("env-"),
            order = 0,
        )
        val slot = environment.createSlot(
            project = application,
        )
        slot.addWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            workflowYaml = """
                name: Deployment
                nodes:
                  - id: av
                    executorId: auto-versioning
                    data:
                        targetProject: ${uid("p-")}
                        targetBranch: main
                        targetPath: gradle.properties
                        targetProperty: version
                        targetVersion: 1.0.0
                        versionRule: $versionRule
                        versionRuleConfig: $versionRuleConfig
            """.trimIndent()
        )
    }

}
