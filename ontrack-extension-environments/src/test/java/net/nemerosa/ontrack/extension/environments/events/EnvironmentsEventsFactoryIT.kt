package net.nemerosa.ontrack.extension.environments.events

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@AsAdminTest
class EnvironmentsEventsFactoryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var environmentsEventsFactory: EnvironmentsEventsFactory

    @Autowired
    private lateinit var eventTemplatingService: EventTemplatingService

    @Autowired
    private lateinit var htmlNotificationEventRenderer: HtmlNotificationEventRenderer

    private fun render(event: Event) =
        eventTemplatingService.renderEvent(event, renderer = htmlNotificationEventRenderer)

    @Test
    fun environmentCreation() {
        environmentTestSupport.withEnvironment { env ->
            val event = environmentsEventsFactory.environmentCreation(env)
            val text = render(event)
            assertEquals("Environment ${env.name} has been created.", text)
        }
    }

    @Test
    fun environmentUpdated() {
        environmentTestSupport.withEnvironment { env ->
            val event = environmentsEventsFactory.environmentUpdated(env)
            val text = render(event)
            assertEquals("Environment ${env.name} has been updated.", text)
        }
    }

    @Test
    fun environmentDeleted() {
        environmentTestSupport.withEnvironment { env ->
            val event = environmentsEventsFactory.environmentDeleted(env)
            val text = render(event)
            assertEquals("Environment ${env.name} has been deleted.", text)
        }
    }


    @Test
    fun slotCreation() {
        slotTestSupport.withSlot { slot ->
            val event = environmentsEventsFactory.slotCreation(slot)
            val text = render(event)
            assertEquals(
                """Slot <a href="http://localhost:3000/extension/environments/slot/${slot.id}">${slot.environment.name}/${slot.project.name}</a> for environment ${slot.environment.name} has been created.""",
                text
            )
        }
    }

    @Test
    fun slotUpdated() {
        slotTestSupport.withSlot { slot ->
            val event = environmentsEventsFactory.slotUpdated(slot)
            val text = render(event)
            assertEquals(
                """Slot <a href="http://localhost:3000/extension/environments/slot/${slot.id}">${slot.environment.name}/${slot.project.name}</a> for environment ${slot.environment.name} has been updated.""",
                text
            )
        }
    }

    @Test
    fun slotDeleted() {
        slotTestSupport.withSlot { slot ->
            val event = environmentsEventsFactory.slotDeleted(slot)
            val text = render(event)
            assertEquals(
                """Slot <a href="http://localhost:3000/project/${slot.project.id}">${slot.project.name}</a> (qualifier = "") for environment ${slot.environment.name} has been deleted.""",
                text
            )
        }
    }


    @Test
    fun pipelineCreation() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val event = environmentsEventsFactory.pipelineCreation(pipeline)
            val text = render(event)
            assertEquals(
                """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> has started.""",
                text
            )
        }
    }

    @Test
    fun pipelineDeploying() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val event = environmentsEventsFactory.pipelineDeploying(pipeline)
            val text = render(event)
            assertEquals(
                """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> is starting its deployment.""",
                text
            )
        }
    }

    @Test
    fun pipelineDeployed() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val event = environmentsEventsFactory.pipelineDeployed(pipeline)
            val text = render(event)
            assertEquals(
                """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> has been deployed.""",
                text
            )
        }
    }

    @Test
    fun pipelineCancelled() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val event = environmentsEventsFactory.pipelineCancelled(pipeline)
            val text = render(event)
            assertEquals(
                """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> has been cancelled.""",
                text
            )
        }
    }

    @Test
    fun pipelineFailed() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val event = environmentsEventsFactory.pipelineFailed(pipeline)
            val text = render(event)
            assertEquals(
                """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> has failed.""",
                text
            )
        }
    }

    @Test
    fun pipelineStatusOverridden() {
        slotTestSupport.withSlotPipeline { pipeline ->
            asGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
                val user = securityService.currentSignature.user.name
                val event = environmentsEventsFactory.pipelineStatusOverridden(pipeline)
                val text = render(event)
                assertEquals(
                    """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> status has been overridden by $user.""",
                    text
                )
            }
        }
    }

    @Test
    fun pipelineStatusChanged() {
        slotTestSupport.withSlotPipeline { pipeline ->
            val event = environmentsEventsFactory.pipelineStatusChanged(pipeline)
            val text = render(event)
            assertEquals(
                """Pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.slot.environment.name}/${pipeline.slot.project.name}#1</a> status has been updated.""",
                text
            )
        }
    }

}