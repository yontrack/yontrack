package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The demo's change log: a project pointing at a mock SCM repository, commits registered on
 * a branch of it, and builds pointing at the commit they were built from.
 *
 * Change logs are one of Yontrack's central features and the demo showed none of it before
 * #1698, for want of any vocabulary for an SCM in [DemoDataset].
 */
class DemoChangeLogSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    private fun dataset(
        scm: ScmSpec? = ScmSpec(repository = "one-repo"),
        scmBranch: String? = "main",
        commits: List<CommitSpec> = listOf(commit("feat: something"), commit("fix: something else")),
    ) = DemoDataset(
        projects = listOf(
            ProjectSpec(
                name = "one",
                description = "",
                scm = scm,
                branches = listOf(
                    BranchSpec(
                        name = "main",
                        description = "",
                        scmBranch = scmBranch,
                        builds = listOf(
                            BuildSpec(
                                name = "1",
                                description = "",
                                creation = BuildCreation.DaysAgo(1),
                                commits = commits,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun commit(
        message: String,
        author: DemoPerson = DemoPerson("Maria Escobar", "maria.escobar@example.com"),
    ) = CommitSpec(message, author)

    @Test
    fun `a project declaring an SCM is pointed at its repository`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset())

        assertTrue("  scm one-repo" in target.snapshot(), target.snapshot())
        assertTrue("  scm branch main" in target.snapshot(), target.snapshot())
    }

    @Test
    fun `every declared commit is registered, and the build points at the last one`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset(commits = listOf(commit("feat: first"), commit("fix: second"))))

        val snapshot = target.snapshot()
        assertTrue("commit main-1-b0bd943 \"feat: first\"" in snapshot, snapshot)
        assertTrue("commit main-2-dfbccd9 \"fix: second\"" in snapshot, snapshot)
        // The build is built from the last commit declared for it: the ones before are the
        // work that went into it, and are what the change log with the previous build shows.
        assertTrue("built from main-2-dfbccd9" in snapshot, snapshot)
    }

    @Test
    fun `each commit is registered with its own author`() {
        val target = InMemoryDemoTarget()

        seed(target).run(
            dataset(
                commits = listOf(
                    commit("feat: first", DemoPerson("Maria Escobar", "maria.escobar@example.com")),
                    commit("fix: second", DemoPerson("Tomas Herrera", "tomas.herrera@example.com")),
                )
            )
        )

        val snapshot = target.snapshot()
        assertTrue(
            "commit main-1-b0bd943 \"feat: first\" by Maria Escobar <maria.escobar@example.com>" in snapshot,
            snapshot,
        )
        assertTrue(
            "commit main-2-dfbccd9 \"fix: second\" by Tomas Herrera <tomas.herrera@example.com>" in snapshot,
            snapshot,
        )
    }

    /**
     * A change log where one name reads on every line looks as made up as one reading `unknown`.
     */
    @Test
    fun `the change log of every project with an SCM is written by several people`() {
        val projects = DemoContent.dataset(emptyList()).projects.filter { it.scm != null }

        assertTrue(projects.isNotEmpty(), "The demo has projects with an SCM")
        projects.forEach { project ->
            val authors = project.commits().map { it.author }.toSet()
            assertTrue(authors.size > 1, "${project.name} has several authors: $authors")
        }
    }

    /**
     * The domain reserved for examples, so that the demo never names anybody's real address.
     */
    @Test
    fun `the authors of the demo have example addresses`() {
        val authors = DemoContent.dataset(emptyList()).projects.flatMap { it.commits() }.map { it.author }.toSet()

        assertTrue(authors.isNotEmpty(), "The demo has authors")
        authors.forEach { author ->
            assertTrue(author.email.endsWith("@example.com"), "$author has an example address")
        }
    }

    @Test
    fun `the issues of the repository are declared before the commits mentioning them`() {
        val target = InMemoryDemoTarget()

        seed(target).run(
            dataset(
                scm = ScmSpec(
                    repository = "one-repo",
                    issues = listOf(IssueSpec("ONE-1", "An issue", type = "feature")),
                ),
                commits = listOf(commit("feat: something, closes ONE-1")),
            )
        )

        val snapshot = target.snapshot()
        // The mock SCM links a commit to an issue when the commit is registered, so an issue
        // declared afterwards would never be linked to anything.
        assertTrue(
            snapshot.indexOf("issue ONE-1") < snapshot.indexOf("commit main-1"),
            snapshot,
        )
    }

    @Test
    fun `re-seeding starts the repository over, so the commit ids do not drift`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset())
        val first = target.snapshot()
        seed(target).run(dataset())
        val second = target.snapshot()

        // The mock SCM keeps its repositories on the server, outliving the projects the
        // reset deletes: without emptying the repository, the second run's commits would
        // continue the first run's numbering and every id would change.
        assertEquals(first, second)
    }

    @Test
    fun `a build declaring commits on a branch with no SCM branch is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(dataset(scmBranch = null))
        }
        assertTrue("SCM branch" in error.message.orEmpty(), error.message.orEmpty())
    }

    @Test
    fun `a branch declaring an SCM branch in a project with no SCM is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(dataset(scm = null))
        }
        assertTrue("no SCM" in error.message.orEmpty(), error.message.orEmpty())
    }

    /**
     * The mock issue service recognises `ABC-123` and nothing else, so a key of another
     * shape is an issue no commit will ever be linked to — and an issues section that
     * silently stays empty.
     */
    @Test
    fun `an issue key the mock issue service would not recognise is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                dataset(scm = ScmSpec("one-repo", issues = listOf(IssueSpec("one-1", "An issue"))))
            )
        }
        assertTrue("one-1" in error.message.orEmpty(), error.message.orEmpty())
    }

    /**
     * The seed deletes before it creates, and the mock SCM is off by default. Learning that
     * on the first commit — after every project is gone — would leave the demo blank, which
     * is the one outcome the reset is designed never to produce.
     */
    @Test
    fun `an instance without the mock SCM is refused before anything is deleted`() {
        val target = InMemoryDemoTarget(scmEnabled = false)
        target.createProject("left-over", "A project a visitor created.")
        val before = target.snapshot()

        val error = assertFailsWith<IllegalStateException> {
            seed(target).run(dataset())
        }

        assertTrue("mock SCM is not enabled" in error.message.orEmpty(), error.message.orEmpty())
        assertEquals(before, target.snapshot(), "The demo is untouched")
    }

    /**
     * Only when the dataset asks for one: a dataset with no SCM must not need the mock SCM
     * to be enabled.
     */
    @Test
    fun `a dataset declaring no SCM does not need the mock SCM`() {
        val target = InMemoryDemoTarget(scmEnabled = false)

        seed(target).run(DemoDataset(projects = listOf(ProjectSpec("one", "", branches = emptyList()))))

        assertEquals(listOf("one"), target.projects().map { it.name })
    }

    @Test
    fun `the demo shows a change log on the main demo project`() {
        val target = InMemoryDemoTarget()

        seed(target).run(DemoContent.dataset(emptyList()))

        val snapshot = target.snapshot()
        assertTrue("  scm ${DemoContent.SERVICE}" in snapshot, snapshot)
        // Conventional-commit subjects, so the semantic change log has sections to group.
        assertTrue(snapshot.contains(Regex("""commit \S+ "feat""")), snapshot)
        assertTrue(snapshot.contains(Regex("""commit \S+ "fix""")), snapshot)
        assertTrue(snapshot.contains(Regex("""commit \S+ "docs""")), snapshot)
    }

    /**
     * The `yontrack` project is seeded from the real change log, whose subjects are
     * overwhelmingly `#1234 Some message` — untyped, and dropped by the semantic renderer.
     * Wiring a change log up there would make the demo of the semantic view showcase its
     * own empty state.
     */
    @Test
    fun `the changelog project has no SCM`() {
        val dataset = DemoContent.dataset(emptyList())

        val changelogProject = dataset.projects.first { it.name == DemoContent.CHANGELOG }

        assertEquals(null, changelogProject.scm)
    }
}

/** Every commit a project declares, on all its branches. */
internal fun ProjectSpec.commits() = branches.flatMap { it.builds }.flatMap { it.commits }
