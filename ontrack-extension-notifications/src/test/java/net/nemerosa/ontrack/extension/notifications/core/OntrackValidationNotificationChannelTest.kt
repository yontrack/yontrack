package net.nemerosa.ontrack.extension.notifications.core

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.exceptions.BranchNotFoundException
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.exceptions.ValidationRunStatusNotFoundException
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OntrackValidationNotificationChannelTest {

    private lateinit var channel: OntrackValidationNotificationChannel
    private lateinit var structureService: StructureService
    private lateinit var eventTemplatingService: EventTemplatingService
    private lateinit var validationRunStatusService: ValidationRunStatusService

    @BeforeEach
    fun before() {
        structureService = mockk()

        eventTemplatingService = mockk()
        every {
            eventTemplatingService.renderEvent(any(), any(), any())
        } answers {
            it.invocation.args[2] as? String? ?: error("No template provided")
        }

        validationRunStatusService = mockk()
        every { validationRunStatusService.getValidationRunStatus(any()) } answers {
            when (val id = firstArg<String>()) {
                ValidationRunStatusID.PASSED -> ValidationRunStatusID.STATUS_PASSED
                ValidationRunStatusID.FAILED -> ValidationRunStatusID.STATUS_FAILED
                else -> throw ValidationRunStatusNotFoundException(id)
            }
        }

        channel = OntrackValidationNotificationChannel(
            eventTemplatingService = eventTemplatingService,
            structureService = structureService,
            securityService = MockSecurityService(),
            runInfoService = mockk(),
            validationRunStatusService = validationRunStatusService,
        )
    }

    @Test
    fun `Getting the project from the event and not found`() {
        val event = mockk<Event>()
        every { event.getEntity<Project>(ProjectEntityType.PROJECT) } throws RuntimeException("Project not found")
        assertFailsWith<RuntimeException> {
            channel.getTargetProject(
                config = OntrackValidationNotificationChannelConfig(validation = "na"),
                event = event,
                context = emptyMap(),
            )
        }
    }

    @Test
    fun `Getting the project from the event and found`() {
        val event = mockk<Event>()
        val project = ProjectFixtures.testProject()
        every { event.getEntity<Project>(ProjectEntityType.PROJECT) } returns project
        assertEquals(
            project,
            channel.getTargetProject(
                config = OntrackValidationNotificationChannelConfig(validation = "na"),
                event = event,
                context = emptyMap(),
            )
        )
    }

    @Test
    fun `Getting the project from its name and not found`() {
        val event = mockk<Event>()

        every {
            structureService.findProjectByName("test")
        } returns Optional.empty()

        assertFailsWith<ProjectNotFoundException> {
            channel.getTargetProject(
                config = OntrackValidationNotificationChannelConfig(
                    project = "test",
                    validation = "na"
                ),
                event = event,
                context = emptyMap(),
            )
        }
    }

    @Test
    fun `Getting the project from its name and found`() {
        val event = mockk<Event>()
        val project = ProjectFixtures.testProject()

        every {
            structureService.findProjectByName("test")
        } returns Optional.of(project)

        assertEquals(
            project,
            channel.getTargetProject(
                config = OntrackValidationNotificationChannelConfig(
                    project = "test",
                    validation = "na"
                ),
                event = event,
                context = emptyMap(),
            )
        )
    }

    @Test
    fun `Getting the branch from the event and not found`() {
        val event = mockk<Event>()
        every { event.getEntity<Branch>(ProjectEntityType.BRANCH) } throws RuntimeException("Branch not found")
        assertFailsWith<RuntimeException> {
            channel.getTargetBranch(
                config = OntrackValidationNotificationChannelConfig(validation = "na"),
                event = event,
                context = emptyMap(),
            )
        }
    }

    @Test
    fun `Getting the branch from the event and found`() {
        val event = mockk<Event>()
        val branch = BranchFixtures.testBranch()
        every { event.getEntity<Branch>(ProjectEntityType.BRANCH) } returns branch
        assertEquals(
            branch,
            channel.getTargetBranch(
                config = OntrackValidationNotificationChannelConfig(validation = "na"),
                event = event,
                context = emptyMap(),
            )
        )
    }

    @Test
    fun `Getting the branch from its name and not found`() {
        val event = mockk<Event>()
        val project = ProjectFixtures.testProject()

        every {
            structureService.findProjectByName(project.name)
        } returns Optional.of(project)

        every {
            structureService.findBranchByName(project.name, "main")
        } returns Optional.empty()

        assertFailsWith<BranchNotFoundException> {
            channel.getTargetBranch(
                config = OntrackValidationNotificationChannelConfig(
                    project = project.name,
                    branch = "main",
                    validation = "na"
                ),
                event = event,
                context = emptyMap(),
            )
        }
    }

    @Test
    fun `Getting the branch from its name and found`() {
        val event = mockk<Event>()
        val branch = BranchFixtures.testBranch(name = "main")

        every {
            structureService.findProjectByName(branch.project.name)
        } returns Optional.of(branch.project)

        every {
            structureService.findBranchByName(branch.project.name, "main")
        } returns Optional.of(branch)

        assertEquals(
            branch,
            channel.getTargetBranch(
                config = OntrackValidationNotificationChannelConfig(
                    project = branch.project.name,
                    branch = "main",
                    validation = "na"
                ),
                event = event,
                context = emptyMap(),
            )
        )
    }

    @Test
    fun `Getting the build from the event and not found`() {
        val event = mockk<Event>()
        every { event.getEntity<Build>(ProjectEntityType.BUILD) } throws RuntimeException("Build not found")
        assertFailsWith<RuntimeException> {
            channel.getTargetBuild(
                config = OntrackValidationNotificationChannelConfig(validation = "na"),
                event = event,
                context = emptyMap(),
            )
        }
    }

    @Test
    fun `Getting the build from the event and found`() {
        val event = mockk<Event>()
        val build = BuildFixtures.testBuild()
        every { event.getEntity<Build>(ProjectEntityType.BUILD) } returns build
        assertEquals(
            build,
            channel.getTargetBuild(
                config = OntrackValidationNotificationChannelConfig(validation = "na"),
                event = event,
                context = emptyMap(),
            )
        )
    }

    @Test
    fun `Getting the build from its name and not found`() {
        val event = mockk<Event>()
        val branch = BranchFixtures.testBranch()

        every {
            structureService.findProjectByName(branch.project.name)
        } returns Optional.of(branch.project)

        every {
            structureService.findBranchByName(branch.project.name, branch.name)
        } returns Optional.of(branch)

        every {
            structureService.findBuildByName(branch.project.name, branch.name, "1.0")
        } returns Optional.empty()

        assertFailsWith<BuildNotFoundException> {
            channel.getTargetBuild(
                config = OntrackValidationNotificationChannelConfig(
                    project = branch.project.name,
                    branch = branch.name,
                    build = "1.0",
                    validation = "na"
                ),
                event = event,
                context = emptyMap(),
            )
        }
    }

    @Test
    fun `Getting the build from its name and found`() {
        val event = mockk<Event>()
        val build = BuildFixtures.testBuild()

        every {
            structureService.findProjectByName(build.branch.project.name)
        } returns Optional.of(build.branch.project)

        every {
            structureService.findBranchByName(build.branch.project.name, build.branch.name)
        } returns Optional.of(build.branch)

        every {
            structureService.findBuildByName(build.branch.project.name, build.branch.name, build.name)
        } returns Optional.of(build)

        assertEquals(
            build,
            channel.getTargetBuild(
                config = OntrackValidationNotificationChannelConfig(
                    project = build.branch.project.name,
                    branch = build.branch.name,
                    build = build.name,
                    validation = "na"
                ),
                event = event,
                context = emptyMap(),
            )
        )
    }

    @Test
    fun `Resolving a null status gives the passed status`() {
        assertEquals(
            ValidationRunStatusID.STATUS_PASSED,
            channel.resolveStatus(null, mockk<Event>(), emptyMap())
        )
    }

    @Test
    fun `Resolving a blank status gives the passed status`() {
        assertEquals(
            ValidationRunStatusID.STATUS_PASSED,
            channel.resolveStatus("", mockk<Event>(), emptyMap())
        )
    }

    @Test
    fun `Resolving a plain status ID`() {
        assertEquals(
            ValidationRunStatusID.STATUS_FAILED,
            channel.resolveStatus("FAILED", mockk<Event>(), emptyMap())
        )
    }

    @Test
    fun `Resolving a template rendered as an empty string is an unknown status`() {
        val event = mockk<Event>()
        val template = "${'$'}{workflow.status}"
        every { eventTemplatingService.renderEvent(event, emptyMap(), template) } returns ""
        assertFailsWith<ValidationRunStatusNotFoundException> {
            channel.resolveStatus(template, event, emptyMap())
        }
    }

    @Test
    fun `Resolving a template trims its rendering`() {
        val event = mockk<Event>()
        val template = "${'$'}{workflow.status}"
        every { eventTemplatingService.renderEvent(event, emptyMap(), template) } returns "  FAILED  "
        assertEquals(
            ValidationRunStatusID.STATUS_FAILED,
            channel.resolveStatus(template, event, emptyMap())
        )
    }

    @Test
    fun `Resolving an unknown status ID`() {
        assertFailsWith<ValidationRunStatusNotFoundException> {
            channel.resolveStatus("NOT_A_STATUS", mockk<Event>(), emptyMap())
        }
    }

    @Test
    fun `Validating a null status`() {
        channel.validateStatus(null)
    }

    @Test
    fun `Validating a valid status ID`() {
        channel.validateStatus("FAILED")
    }

    @Test
    fun `Validating a template status is not checked`() {
        channel.validateStatus("${'$'}{STATUS}")
    }

    @Test
    fun `Validating an unknown status ID`() {
        val ex = assertFailsWith<EventSubscriptionConfigException> {
            channel.validateStatus("NOT_A_STATUS")
        }
        assertEquals("Status [NOT_A_STATUS] is not a valid validation run status ID.", ex.innerMessage)
    }
}
