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
        every {
            validationRunStatusService.getValidationRunStatus(any())
        } answers {
            when (val id = it.invocation.args[0] as String) {
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
    fun `Resolving the status when not configured defaults to passed`() {
        val event = mockk<Event>()
        assertEquals(
            ValidationRunStatusID.STATUS_PASSED,
            channel.resolveStatus(status = null, event = event, context = emptyMap()),
        )
    }

    @Test
    fun `Resolving the status when blank defaults to passed`() {
        val event = mockk<Event>()
        assertEquals(
            ValidationRunStatusID.STATUS_PASSED,
            channel.resolveStatus(status = "", event = event, context = emptyMap()),
        )
    }

    @Test
    fun `Resolving the status from a template`() {
        val event = mockk<Event>()
        assertEquals(
            ValidationRunStatusID.STATUS_FAILED,
            channel.resolveStatus(status = "FAILED", event = event, context = emptyMap()),
        )
    }

    @Test
    fun `Resolving the status when a configured template renders blank fails`() {
        val event = mockk<Event>()
        every {
            eventTemplatingService.renderEvent(event, emptyMap(), "\${blank}")
        } returns ""
        assertFailsWith<ValidationRunStatusNotFoundException> {
            channel.resolveStatus(status = "\${blank}", event = event, context = emptyMap())
        }
    }

    @Test
    fun `Resolving the status trims whitespace from the rendered template`() {
        val event = mockk<Event>()
        every {
            eventTemplatingService.renderEvent(event, emptyMap(), "\${status}")
        } returns "  FAILED  "
        assertEquals(
            ValidationRunStatusID.STATUS_FAILED,
            channel.resolveStatus(status = "\${status}", event = event, context = emptyMap()),
        )
    }

    @Test
    fun `Resolving an invalid status fails`() {
        val event = mockk<Event>()
        assertFailsWith<ValidationRunStatusNotFoundException> {
            channel.resolveStatus(status = "NOT_A_STATUS", event = event, context = emptyMap())
        }
    }

    @Test
    fun `Validating the status accepts none`() {
        channel.validateStatus(null)
    }

    @Test
    fun `Validating the status accepts a valid literal`() {
        channel.validateStatus("FAILED")
    }

    @Test
    fun `Validating the status accepts a template without checking it`() {
        channel.validateStatus("\${STATUS}")
    }

    @Test
    fun `Validating the status rejects an invalid literal`() {
        assertFailsWith<EventSubscriptionConfigException> {
            channel.validateStatus("NOT_A_STATUS")
        }
    }

}