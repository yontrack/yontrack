package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The project permissions page (#2164) shows a group holding two roles on one project (#2169): roles
 * add up, and the demo has a group to show it with.
 */
class DemoProjectPermissionsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    private fun InMemoryDemoTarget.groupRoles(): Map<String, Map<String, List<DemoProjectRole>>> =
        projects()
            .filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
            .filter { it.groupRoles.isNotEmpty() }
            .associate { it.name to it.groupRoles }

    @Test
    fun `the Release engineers group holds Participant and Auto-versioning controller on the sample service`() {
        val target = InMemoryDemoTarget()

        seed(target).run(DemoContent.dataset(emptyList()))

        assertEquals(
            mapOf(
                DemoContent.SERVICE to mapOf(
                    DemoContent.RELEASE_ENGINEERS to listOf(DemoProjectRole.PARTICIPANT, DemoProjectRole.AUTO_VERSIONING_CONTROLLER),
                ),
            ),
            target.groupRoles(),
        )
    }

    @Test
    fun `a second run finds the group the first one created, and grants the same roles again`() {
        val target = InMemoryDemoTarget()
        val dataset = DemoContent.dataset(emptyList())

        seed(target).run(dataset)
        seed(target).run(dataset)

        assertEquals(listOf(DemoContent.RELEASE_ENGINEERS), target.accountGroupNames())
        assertEquals(
            mapOf(DemoContent.RELEASE_ENGINEERS to listOf(DemoProjectRole.PARTICIPANT, DemoProjectRole.AUTO_VERSIONING_CONTROLLER)),
            target.groupRoles()[DemoContent.SERVICE],
        )
    }

    @Test
    fun `a group somebody created before the demo is reused, not created again`() {
        val target = InMemoryDemoTarget()
        target.createAccountGroup(DemoContent.RELEASE_ENGINEERS, "Created by hand.")

        seed(target).run(DemoContent.dataset(emptyList()))

        assertEquals(listOf(DemoContent.RELEASE_ENGINEERS), target.accountGroupNames())
    }

    @Test
    fun `a seeding account which may not manage account groups is refused before anything is deleted`() {
        val target = InMemoryDemoTarget(accountGroupsManaged = false)
        target.createProject("left-over", "A project a visitor created.")

        assertFailsWith<IllegalStateException> {
            seed(target).run(DemoContent.dataset(emptyList()))
        }

        assertEquals(listOf("left-over"), target.projects().map { it.name })
    }

    @Test
    fun `a project granting a role to a group the dataset never declares is caught before anything is deleted`() {
        val target = InMemoryDemoTarget()
        target.createProject("left-over", "A project a visitor created.")

        val error = assertFailsWith<IllegalArgumentException> {
            seed(target).run(
                DemoDataset(
                    projects = listOf(
                        ProjectSpec(
                            name = "p",
                            description = "",
                            branches = listOf(BranchSpec(name = "main", description = "")),
                            groupRoles = listOf(
                                ProjectGroupRolesSpec("Ghosts", listOf(DemoProjectRole.PARTICIPANT)),
                            ),
                        ),
                    ),
                )
            )
        }

        assertTrue("Ghosts" in error.message.orEmpty(), error.message.orEmpty())
        assertTrue("never creates" in error.message.orEmpty(), error.message.orEmpty())
        assertEquals(listOf("left-over"), target.projects().map { it.name })
    }

    @Test
    fun `a group granted the same role twice on a project is a problem`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                DemoDataset(
                    groups = listOf(AccountGroupSpec("Release engineers", "")),
                    projects = listOf(
                        ProjectSpec(
                            name = "p",
                            description = "",
                            branches = listOf(BranchSpec(name = "main", description = "")),
                            groupRoles = listOf(
                                ProjectGroupRolesSpec("Release engineers", listOf(DemoProjectRole.PARTICIPANT)),
                                ProjectGroupRolesSpec("Release engineers", listOf(DemoProjectRole.PARTICIPANT)),
                            ),
                        ),
                    ),
                )
            )
        }

        assertTrue(DemoProjectRole.PARTICIPANT.id in error.message.orEmpty(), error.message.orEmpty())
        assertTrue("twice" in error.message.orEmpty(), error.message.orEmpty())
    }
}
