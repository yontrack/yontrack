package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DemoSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private val changelog = listOf(
        ChangelogEntry("a1b2c3d", "#1664 Drive the demo deployment through the CLI", LocalDateTime.of(2026, 8, 30, 14, 0)),
        ChangelogEntry("e4f5a6b", "#1680 Report the full version in the running application", LocalDateTime.of(2026, 8, 31, 9, 30)),
    )

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    @Test
    fun `running it twice in a row yields the same demo state`() {
        val target = InMemoryDemoTarget()
        val dataset = DemoContent.dataset(changelog)

        seed(target).run(dataset)
        val first = target.snapshot()

        seed(target).run(dataset)
        val second = target.snapshot()

        assertEquals(first, second)
    }

    @Test
    fun `the reset deletes whatever was there before`() {
        val target = InMemoryDemoTarget()
        target.createProject("left-over", "A project a visitor created.")
        target.createEnvironment("left-over-env", 500, "An environment nobody remembers.")

        seed(target).run(DemoContent.dataset(changelog))

        val snapshot = target.snapshot()
        assertTrue("left-over" !in snapshot, "The left-over project is gone")
        assertTrue("left-over-env" !in snapshot, "The left-over environment is gone")
    }

    @Test
    fun `the curated dataset and the changelog project are both created`() {
        val target = InMemoryDemoTarget()

        seed(target).run(DemoContent.dataset(changelog))

        assertEquals(
            listOf(
                DemoContent.LIBRARY,
                DemoContent.SERVICE,
                DemoContent.UI,
                DemoContent.CHANGELOG,
            ),
            target.projects().map { it.name },
        )
        assertEquals(
            listOf(DemoContent.STAGING, DemoContent.PRODUCTION),
            target.environments().map { it.name },
        )
    }

    @Test
    fun `one build per changelog entry, named after the commit`() {
        val target = InMemoryDemoTarget()

        seed(target).run(DemoContent.dataset(changelog))

        val snapshot = target.snapshot()
        changelog.forEach { entry ->
            assertTrue(
                "build ${entry.id} \"${entry.message}\" at ${entry.time}" in snapshot,
                "Build for commit ${entry.id}",
            )
        }
    }

    @Test
    fun `an empty changelog still leaves the changelog project standing`() {
        val target = InMemoryDemoTarget()

        seed(target).run(DemoContent.dataset(emptyList()))

        assertTrue(target.projects().any { it.name == DemoContent.CHANGELOG })
    }

    @Test
    fun `build creation times follow the clock, not the wall clock`() {
        val target = InMemoryDemoTarget()

        seed(target).run(DemoContent.dataset(changelog))

        // The most recent curated build is one day old, at 09:00.
        assertTrue(
            "at 2026-08-31T09:00" in target.snapshot(),
            "A build dated one day before the fixed clock",
        )
    }

    @Test
    fun `the demo dashboard is reset like everything else`() {
        val target = InMemoryDemoTarget()
        target.saveDashboard(DemoDashboard("some-uuid", "A visitor's dashboard", emptyList()))

        seed(target).run(DemoContent.dataset(changelog))

        assertEquals(listOf("Yontrack demo"), target.dashboards().map { it.name })
    }

    /**
     * The seed deletes before it creates, so a dataset the server would reject must be
     * caught before anything is gone. `release/1.3` — an illegal Yontrack entity name — is
     * the case that motivated this: it was found half-way through a real reset.
     */
    @Test
    fun `an invalid dataset is refused before anything is deleted`() {
        val target = InMemoryDemoTarget()
        seed(target).run(DemoContent.dataset(changelog))
        val before = target.snapshot()

        val error = assertFailsWith<IllegalArgumentException> {
            seed(target).run(
                DemoDataset(
                    projects = listOf(
                        ProjectSpec(
                            name = "one",
                            description = "",
                            branches = listOf(BranchSpec(name = "release/1.3", description = "")),
                        ),
                    ),
                )
            )
        }

        assertTrue("release/1.3" in error.message.orEmpty())
        assertEquals(before, target.snapshot(), "The demo is untouched")
    }

    @Test
    fun `a dataset pointing at a build it never creates is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                DemoDataset(
                    projects = listOf(
                        ProjectSpec(
                            name = "one",
                            description = "",
                            branches = listOf(
                                BranchSpec(
                                    name = "main",
                                    description = "",
                                    builds = listOf(
                                        BuildSpec(
                                            name = "1",
                                            description = "",
                                            creation = BuildCreation.DaysAgo(1),
                                            links = listOf(BuildRef("other", "main", "1")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            )
        }
        assertTrue("never creates" in error.message.orEmpty())
    }

    @Test
    fun `a build promoted to a level its branch does not declare is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                DemoDataset(
                    projects = listOf(
                        ProjectSpec(
                            name = "one",
                            description = "",
                            branches = listOf(
                                BranchSpec(
                                    name = "main",
                                    description = "",
                                    builds = listOf(
                                        BuildSpec(
                                            name = "1",
                                            description = "",
                                            creation = BuildCreation.DaysAgo(1),
                                            promotionLevels = listOf("BRONZE"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            )
        }
        assertTrue("BRONZE" in error.message.orEmpty())
    }

    @Test
    fun `every problem is reported at once, not one reset at a time`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                DemoDataset(
                    projects = listOf(
                        ProjectSpec(
                            name = "not a name",
                            description = "",
                            branches = listOf(BranchSpec(name = "release/1.3", description = "")),
                        ),
                    ),
                )
            )
        }
        assertTrue("not a name" in error.message.orEmpty())
        assertTrue("release/1.3" in error.message.orEmpty())
    }
}
