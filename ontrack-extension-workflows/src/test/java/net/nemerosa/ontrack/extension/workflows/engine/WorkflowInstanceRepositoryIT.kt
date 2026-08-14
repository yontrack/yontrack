package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.WorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowFixtures
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.events.MockEventType
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.trigger.UserTrigger
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkflowInstanceRepositoryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Autowired
    private lateinit var serializableEventService: SerializableEventService

    @Autowired
    private lateinit var workflowTestSupport: WorkflowTestSupport

    @Autowired
    private lateinit var userTrigger: UserTrigger

    @Test
    fun `Saving and retrieving one workflow instance from a database`() {
        val instance = createInstance(
            workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml),
            event = serializableEventService.dehydrate(
                MockEventType.mockEvent("Some text")
            ),
            contexts = emptyMap(),
            triggerData = workflowTestSupport.testTriggerData(),
        )
        workflowInstanceRepository.createInstance(instance)

        assertNotNull(workflowInstanceRepository.findWorkflowInstance(instance.id)) { saved ->
            assertEquals(
                instance.truncateTimestamp(),
                saved.truncateTimestamp(),
            )
        }
    }

    @Test
    fun `Filtering workflows by name`() {
        val instances = (1..2).map {
            createInstance(
                workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                    .rename { uid("w-") },
                event = serializableEventService.dehydrate(
                    MockEventType.mockEvent("Some text")
                ),
                contexts = emptyMap(),
                triggerData = workflowTestSupport.testTriggerData(),
            ).apply {
                workflowInstanceRepository.createInstance(this)
            }
        }

        val found = workflowInstanceRepository.findInstances(
            WorkflowInstanceFilter(name = instances[1].workflow.name)
        ).pageItems

        assertEquals(1, found.size)
        assertEquals(instances[1].id, found.first().id)
    }

    @Test
    fun `Filtering workflows by ID`() {
        val instances = (1..2).map {
            createInstance(
                workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                    .rename { uid("w-") },
                event = serializableEventService.dehydrate(
                    MockEventType.mockEvent("Some text")
                ),
                contexts = emptyMap(),
                triggerData = workflowTestSupport.testTriggerData(),
            ).apply {
                workflowInstanceRepository.createInstance(this)
            }
        }

        val found = workflowInstanceRepository.findInstances(
            WorkflowInstanceFilter(id = instances[1].id)
        ).pageItems

        assertEquals(1, found.size)
        assertEquals(instances[1].id, found.first().id)
    }

    @Test
    fun `Getting the statuses of several nodes at once`() {
        val instance = createInstance(
            workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml),
            event = serializableEventService.dehydrate(
                MockEventType.mockEvent("Some text")
            ),
            contexts = emptyMap(),
            triggerData = workflowTestSupport.testTriggerData(),
        )
        workflowInstanceRepository.createInstance(instance)
        workflowInstanceRepository.nodeSuccess(instance.id, "start", null, null)

        val statuses = workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start", "end", "unknown"))

        assertEquals(
            mapOf(
                "start" to WorkflowInstanceNodeStatus.SUCCESS,
                "end" to WorkflowInstanceNodeStatus.CREATED,
            ),
            statuses,
            "Unknown nodes are simply absent from the result"
        )
    }

    @Test
    fun `Getting the statuses of no node at all`() {
        val instance = createInstance(
            workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml),
            event = serializableEventService.dehydrate(
                MockEventType.mockEvent("Some text")
            ),
            contexts = emptyMap(),
            triggerData = workflowTestSupport.testTriggerData(),
        )
        workflowInstanceRepository.createInstance(instance)

        assertEquals(emptyMap(), workflowInstanceRepository.getNodeStatuses(instance.id, emptyList()))
    }

    @Test
    fun `Filtering workflows by ID takes precedence`() {
        val instances = (1..2).map {
            createInstance(
                workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                    .rename { uid("w-") },
                event = serializableEventService.dehydrate(
                    MockEventType.mockEvent("Some text")
                ),
                contexts = emptyMap(),
                triggerData = workflowTestSupport.testTriggerData(),
            ).apply {
                workflowInstanceRepository.createInstance(this)
            }
        }

        val found = workflowInstanceRepository.findInstances(
            WorkflowInstanceFilter(
                id = instances[1].id,
                name = instances[0].workflow.name,
            )
        ).pageItems

        assertEquals(1, found.size)
        assertEquals(instances[1].id, found.first().id)
    }

    @Test
    fun `Filtering workflows by status`() {
        val instances = (1..2).map { no ->
            createInstance(
                workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                    .rename { uid("w-") },
                event = serializableEventService.dehydrate(
                    MockEventType.mockEvent("Some text")
                ),
                contexts = emptyMap(),
                triggerData = workflowTestSupport.testTriggerData(),
            ).apply {
                workflowInstanceRepository.createInstance(this)
                if (no == 1) {
                    workflowInstanceRepository.stopInstance(id)
                }
            }
        }

        val found = workflowInstanceRepository.findInstances(
            WorkflowInstanceFilter(status = WorkflowInstanceStatus.STOPPED)
        ).pageItems

        assertEquals(1, found.size)
        assertEquals(instances[0].id, found.first().id)
    }

    @Test
    fun `Filtering workflows by trigger ID`() {
        workflowInstanceRepository.clearAll()
        val instances = (1..2).map { no ->
            createInstance(
                workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                    .rename { uid("w-") },
                event = serializableEventService.dehydrate(
                    MockEventType.mockEvent("Some text")
                ),
                contexts = emptyMap(),
                triggerData = if (no == 1) {
                    workflowTestSupport.testTriggerData()
                } else {
                    asUser {
                        userTrigger.createUserTriggerData()
                    }
                },
            ).apply {
                workflowInstanceRepository.createInstance(this)
                if (no == 1) {
                    workflowInstanceRepository.stopInstance(id)
                }
            }
        }

        val found = workflowInstanceRepository.findInstances(
            WorkflowInstanceFilter(triggerId = userTrigger.id)
        ).pageItems

        assertEquals(1, found.size, "Only one result")
        assertEquals(instances[1].id, found.first().id)
    }

    @Test
    fun `Filtering workflows by trigger data`() {
        workflowInstanceRepository.clearAll()
        asUser {
            val username = securityService.currentUser?.name
            val instances = (1..3).map { no ->
                createInstance(
                    workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                        .rename { uid("w-") },
                    event = serializableEventService.dehydrate(
                        MockEventType.mockEvent("Some text")
                    ),
                    contexts = emptyMap(),
                    triggerData = if (no == 1) {
                        workflowTestSupport.testTriggerData()
                    } else if (no == 2) {
                        asUser {
                            userTrigger.createUserTriggerData()
                        }
                    } else {
                        userTrigger.createUserTriggerData()
                    },
                ).apply {
                    workflowInstanceRepository.createInstance(this)
                }
            }

            val found = workflowInstanceRepository.findInstances(
                WorkflowInstanceFilter(
                    triggerId = userTrigger.id,
                    triggerData = username,
                )
            ).pageItems

            assertEquals(1, found.size, "Only one result")
            assertEquals(instances[2].id, found.first().id)
        }
    }

    @Test
    fun `Cleaning of workflow instances`() {
        asAdmin {
            // Removing all previous instances for the test
            workflowInstanceRepository.clearAll()
            // Reference time
            val now = Time.now
            // Saving an instance two days ago
            val old = WorkflowInstanceFixtures.simpleLinear(
                timestamp = now - Duration.ofDays(2)
            )
            workflowInstanceRepository.createInstance(old)
            // Saving an instance now
            val recent = WorkflowInstanceFixtures.simpleLinear(
                timestamp = now
            )
            workflowInstanceRepository.createInstance(recent)
            // Launching the cleanup
            workflowInstanceRepository.cleanup(now - Duration.ofDays(1))
            // Only the most recent instance is present
            assertNull(workflowInstanceRepository.findWorkflowInstance(old.id), "Old instance is gone")
            assertNotNull(workflowInstanceRepository.findWorkflowInstance(recent.id), "Recent instance has been kept")
        }
    }
}
