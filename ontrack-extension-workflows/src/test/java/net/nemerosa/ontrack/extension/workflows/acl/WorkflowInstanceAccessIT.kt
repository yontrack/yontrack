package net.nemerosa.ontrack.extension.workflows.acl

import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowFixtures
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngine
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowRegistry
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.model.events.MockEventType
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Authorization of the workflow instance reads - see #1739.
 *
 * Instances are created through the engine rather than straight through the repository: the engine
 * stores them in a transaction of their own, which is the only way the by-id GraphQL read - which
 * also runs in a new transaction - can see them from a transactional test.
 */
class WorkflowInstanceAccessIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Autowired
    private lateinit var workflowInstanceAccessService: WorkflowInstanceAccessService

    @Autowired
    private lateinit var workflowRegistry: WorkflowRegistry

    @Autowired
    private lateinit var workflowEngine: WorkflowEngine

    // --------------------------------------------------------------------------------------------
    // 1. The listing is admin-only
    // --------------------------------------------------------------------------------------------

    @Test
    fun `Listing the workflow instances is granted to a user holding the workflow audit right`() {
        val instance = asAdmin { startInstance(ownerlessEvent()) }
        asUserWith<WorkflowAudit> {
            run(
                """{
                    workflowInstances(id: "${instance.id}") {
                        pageItems { id }
                    }
                }"""
            ) { data ->
                assertEquals(
                    listOf(instance.id),
                    data.path("workflowInstances").path("pageItems").map { it.path("id").asText() }
                )
            }
        }
    }

    @Test
    fun `Listing the workflow instances is denied to a user without the workflow audit right`() {
        val instance = asAdmin { startInstance(ownerlessEvent()) }
        asUser {
            runWithMatchingError(
                """{
                    workflowInstances(id: "${instance.id}") {
                        pageItems { id }
                    }
                }""",
                errorMessage = ACCESS_DENIED,
            )
        }
    }

    @Test
    fun `Listing the workflow instances is denied even to a user who can view the project of an instance`() {
        val (project, instance) = asAdmin {
            val p = project()
            p to startInstance(eventFor(p))
        }
        asUserWithView(project) {
            runWithMatchingError(
                """{
                    workflowInstances(id: "${instance.id}") {
                        pageItems { id }
                    }
                }""",
                errorMessage = ACCESS_DENIED,
            )
        }
    }

    // --------------------------------------------------------------------------------------------
    // 2. The by-id query checks a derived owner
    // --------------------------------------------------------------------------------------------

    @Test
    fun `An instance whose event names a project is readable by a user who can view that project`() {
        val (project, instance) = asAdmin {
            val p = project()
            p to startInstance(eventFor(p))
        }
        asUserWithView(project) {
            assertInstanceReadable(instance)
        }
    }

    @Test
    fun `An instance whose event names a project is not readable by a user who cannot view it`() {
        withNoGrantViewToAll {
            val instance = asAdmin { startInstance(eventFor(project())) }
            asUser {
                assertInstanceNotReadable(instance)
            }
        }
    }

    @Test
    fun `An instance whose event names a project is not readable by a user who can view another project`() {
        withNoGrantViewToAll {
            val (other, instance) = asAdmin {
                val other = project()
                other to startInstance(eventFor(project()))
            }
            asUserWithView(other) {
                assertInstanceNotReadable(instance)
            }
        }
    }

    @Test
    fun `An instance whose event names only a build is readable through the build's project`() {
        // A hand-built event carrying a BUILD but no PROJECT: the owner is derived by walking the
        // most specific entity the event names up to its project.
        withNoGrantViewToAll {
            val (build, instance) = asAdmin {
                val b = project().branch().build()
                b to startInstance(eventForBuildOnly(b))
            }
            asUserWithView(build) {
                assertInstanceReadable(instance)
            }
            asUser {
                assertInstanceNotReadable(instance)
            }
        }
    }

    @Test
    fun `An instance is readable by an administrator whatever the project its event names`() {
        val instance = asAdmin { startInstance(eventFor(project())) }
        val administrator = asAdmin { asGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }
        administrator.call {
            assertInstanceReadable(instance)
        }
    }

    // --------------------------------------------------------------------------------------------
    // 3. Ownerless instances
    // --------------------------------------------------------------------------------------------

    @Test
    fun `An instance with no derivable project is readable only with the workflow audit right`() {
        withNoGrantViewToAll {
            val instance = asAdmin { startInstance(ownerlessEvent()) }
            asUserWith<WorkflowAudit> {
                assertInstanceReadable(instance)
            }
            asUser {
                assertInstanceNotReadable(instance)
            }
        }
    }

    @Test
    fun `An instance launched outside of any entity is readable only with the workflow audit right`() {
        // This is what `launchWorkflow` produces: a WORKFLOW_STANDALONE event with empty entities.
        withNoGrantViewToAll {
            val instanceId = workflowTestSupport.registerLaunchAndWaitForWorkflow(simpleWorkflowYaml())
            val instance = asAdmin { workflowInstanceRepository.findWorkflowInstance(instanceId) }
                ?: error("Cannot find the instance back")
            asUser {
                assertInstanceNotReadable(instance)
            }
            asUserWith<WorkflowAudit> {
                assertInstanceReadable(instance)
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // 4. Orphaned instances - the case `hydrate` gets wrong
    // --------------------------------------------------------------------------------------------

    @Test
    fun `An instance naming a deleted entity is denied cleanly and never throws`() {
        withNoGrantViewToAll {
            val (project, instance) = asAdmin {
                val p = project()
                val b = p.branch().build()
                val instance = startInstance(eventForBuildOnly(b))
                // The build goes away, the instance stays: instances live 14 days and routinely
                // outlive the builds and branches they name.
                structureService.deleteBuild(b.id)
                p to instance
            }
            asUserWithView(project) {
                // No crash: a stale id resolves to nothing and the read is simply denied
                assertInstanceNotReadable(instance)
            }
            asUserWith<WorkflowAudit> {
                assertInstanceReadable(instance)
            }
        }
    }

    @Test
    fun `An instance naming a deleted project is denied cleanly and never throws`() {
        withNoGrantViewToAll {
            val instance = asAdmin {
                val p = project()
                val instance = startInstance(eventFor(p))
                structureService.deleteProject(p.id)
                instance
            }
            asUser {
                assertInstanceNotReadable(instance)
            }
            asUserWith<WorkflowAudit> {
                assertInstanceReadable(instance)
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // 7. The engine and the repository stay open, and workflows still run
    // --------------------------------------------------------------------------------------------

    @Test
    fun `A workflow started by a plain project user still runs to completion`() {
        val instanceId = asAdmin {
            val p = project()
            p.asUserWithView {
                startInstance(eventFor(p)).id
            }
        }
        // The nodes run on the queue threads: a read check on the engine or on the repository would
        // stall them here.
        workflowTestSupport.waitForWorkflowInstance(instanceId)
        val instance = asAdmin { workflowInstanceRepository.findWorkflowInstance(instanceId) }
            ?: error("Cannot find the instance back")
        assertTrue(instance.status.finished, "Workflow has run to completion")
    }

    // --------------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------------

    private fun assertInstanceReadable(instance: WorkflowInstance) {
        assertTrue(
            workflowInstanceAccessService.isWorkflowInstanceAccessible(instance),
            "Instance is accessible",
        )
        workflowInstanceAccessService.checkWorkflowInstanceAccess(instance)
        run(
            """{
                workflowInstance(id: "${instance.id}") { id }
            }"""
        ) { data ->
            assertEquals(instance.id, data.path("workflowInstance").path("id").asText())
        }
    }

    private fun assertInstanceNotReadable(instance: WorkflowInstance) {
        assertFalse(
            workflowInstanceAccessService.isWorkflowInstanceAccessible(instance),
            "Instance is not accessible",
        )
        // The by-id query answers `null` rather than throwing: an exception would leak the existence
        // of the instance.
        run(
            """{
                workflowInstance(id: "${instance.id}") { id }
            }"""
        ) { data ->
            assertTrue(data.path("workflowInstance").isNull, "No instance returned")
        }
    }

    private fun simpleWorkflowYaml() =
        WorkflowFixtures.simpleLinearWorkflowYaml.replace("Simple Linear", uid("w-"))

    private fun startInstance(event: SerializableEvent): WorkflowInstance {
        val record = securityService.asAdmin {
            val workflowId = workflowRegistry.saveYamlWorkflow(simpleWorkflowYaml())
            workflowRegistry.findWorkflow(workflowId)
        } ?: error("Cannot find the workflow back")
        return workflowEngine.startWorkflow(
            workflow = record.workflow,
            event = event,
            triggerData = testTriggerData(),
        )
    }

    private fun eventFor(project: Project): SerializableEvent =
        MockEventType.serializedMockEvent("Some text").withEntity(project)

    private fun eventForBuildOnly(build: Build): SerializableEvent =
        MockEventType.serializedMockEvent("Some text").withEntity(build)

    private fun ownerlessEvent(): SerializableEvent =
        MockEventType.serializedMockEvent("Some text")

    companion object {
        private const val ACCESS_DENIED = "Global function 'WorkflowAudit' is not granted."
    }
}
