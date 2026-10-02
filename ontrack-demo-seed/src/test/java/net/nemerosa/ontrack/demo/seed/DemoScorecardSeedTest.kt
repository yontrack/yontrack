package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The delivery scorecard of the demo (#1906): two estates over overlapping projects, and the
 * backdated history their readings are read from.
 *
 * The readings themselves are computed by the server, so what is pinned here is what they are
 * computed FROM - the estates, the builds, the test runs and the deployments - and that the seed
 * asks for them to be computed at all.
 */
class DemoScorecardSeedTest {

    private val now = LocalDateTime.of(2026, 9, 1, 10, 15, 30)
    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { seed(it).run(DemoContent.dataset(emptyList())) }

    private val dataset = DemoContent.dataset(emptyList())

    /** The projects of the dataset an estate selects: those carrying all of its labels. */
    private fun projectsOf(estate: String): Set<String> {
        val spec = dataset.estates.single { it.name == estate }
        return dataset.projects.filter { it.labels.containsAll(spec.labels) }.map { it.name }.toSet()
    }

    /** The project scorecard widget (#1938) opens on its default set: no set in its configuration. */
    @Test
    fun `the demo dashboard has a project scorecard widget on the scorecard project, on its default set`() {
        val widget = seeded().dashboards()
            .filterIsInstance<InMemoryDemoTarget.InMemoryDashboard>()
            .single().dashboard.widgets
            .single { it.key == "extension/scorecard/ProjectScorecard" }

        assertEquals(DemoContent.VISITS, widget.config.path("project").asString())
        assertTrue(widget.config.path("set").isMissingNode, "No set: the widget shows the default one")
        // The default set is the first estate by name
        assertEquals(setOf(DemoContent.ESTATE_PRODUCTS, DemoContent.ESTATE_PRODUCTION), dataset.estates.filter {
            DemoContent.VISITS in projectsOf(it.name)
        }.map { it.name }.toSet())
        assertTrue(DemoContent.ESTATE_PRODUCTION < DemoContent.ESTATE_PRODUCTS, "The widget opens on ${DemoContent.ESTATE_PRODUCTION}")
    }

    @Test
    fun `the demo has two estates, one read up to GOLD and one up to production`() {
        val target = seeded()

        assertEquals(
            listOf(DemoContent.ESTATE_PRODUCTS, DemoContent.ESTATE_PRODUCTION),
            target.estates().map { it.name },
        )
        val estates = dataset.estates.associateBy { it.name }
        assertEquals(
            EstateMarkerSpec.Promotion(DemoContent.GOLD),
            estates.getValue(DemoContent.ESTATE_PRODUCTS).marker,
        )
        assertEquals(
            EstateMarkerSpec.Environment(DemoContent.PRODUCTION),
            estates.getValue(DemoContent.ESTATE_PRODUCTION).marker,
        )
    }

    /**
     * Overlapping, not identical: a project in both reads differently in each, which is what the
     * estate columns of its scorecard are for, and a project in one only shows that an estate is a
     * selection.
     */
    @Test
    fun `the estates select overlapping projects`() {
        val products = projectsOf(DemoContent.ESTATE_PRODUCTS)
        val production = projectsOf(DemoContent.ESTATE_PRODUCTION)

        assertEquals(setOf(DemoContent.SERVICE, DemoContent.VISITS), products intersect production)
        assertTrue((products - production).isNotEmpty(), "A product which is not in production")
        assertTrue((production - products).isNotEmpty(), "Something in production which is not a product")
        // README *Demo*: the findings are read by the estate of the products (#1912)
        assertTrue(DemoContent.SECURITY in products, "The findings project is in the products estate")
    }

    /**
     * Targets set, but not on everything: a reading an estate does not judge is shown without a
     * verdict, and the demo has to have one to show it.
     */
    @Test
    fun `the estates set targets, and leave one reading without any`() {
        dataset.estates.forEach { estate ->
            assertTrue(
                estate.readings.any { it.target != null },
                "Estate ${estate.name} judges some of its readings",
            )
        }
        val products = dataset.estates.single { it.name == DemoContent.ESTATE_PRODUCTS }
        assertTrue(
            ReadingKeys.QUALITY_TEST_FLAKINESS !in products.readings.map { it.key },
            "The flakiness of the tests is shown in the products estate with no target",
        )
        assertTrue(
            dataset.estates.any { estate -> estate.readings.any { it.windowDays != null } },
            "One estate reads a reading over a window of its own",
        )
    }

    @Test
    fun `the scorecard project has about ninety days of history`() {
        val target = seeded()
        val builds = target.projects()
            .filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
            .single { it.name == DemoContent.VISITS }
            .branches.single().builds

        val oldest = Duration.between(builds.first().creation, now).toDays()
        assertTrue(oldest in 80..90, "The oldest build is within the 90-day window of the readings: $oldest days")
        assertTrue(builds.size >= 12, "Enough builds for the readings to have a median")
        assertTrue(
            builds.all { build -> build.testRuns.isNotEmpty() },
            "Every build runs its tests, which is what the test readings read",
        )
    }

    /**
     * What the flakiness reading counts: on the same build and the same stamp, a failed run
     * followed by a passed one.
     */
    @Test
    fun `the demo has a flaky build`() {
        val target = seeded()
        val flaky = target.projects()
            .filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
            .flatMap { it.branches }
            .flatMap { it.builds }
            .filter { build ->
                build.testRuns.groupBy { it.run.validationStamp }.values.any { runs ->
                    val statuses = runs.map { it.status }
                    val failure = statuses.indexOf(ValidationStatus.FAILED)
                    failure >= 0 && ValidationStatus.PASSED in statuses.drop(failure + 1)
                }
            }
            .map { "${it.branch.project.name}/${it.name}" }

        assertEquals(listOf("${DemoContent.VISITS}/206"), flaky)
    }

    @Test
    fun `the demo has a build whose tests fail`() {
        val target = seeded()
        val failed = target.projects()
            .filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
            .single { it.name == DemoContent.VISITS }
            .branches.single().builds
            .filter { build -> build.testRuns.last().status == ValidationStatus.FAILED }

        assertEquals(listOf("203"), failed.map { it.name })
        assertTrue(failed.single().promotions.none { it.first == DemoContent.GOLD }, "Never promoted to GOLD")
    }

    /**
     * What the success rate and the time to restore of an environment read: a deployment which
     * failed, and the one which restored production after it. And a cancelled one, which both
     * leave out.
     */
    @Test
    fun `the production history has a failed deployment followed by a done one, and a cancelled one`() {
        val target = seeded()
        val slot = target.environments()
            .flatMap { (it as InMemoryDemoTarget.InMemoryEnvironment).slots }
            .single {
                it.environment.name == DemoContent.PRODUCTION &&
                        it.project.name == DemoContent.VISITS &&
                        it.qualifier == ""
            }

        val stops = slot.deployments.map { it.stopAt }
        val failed = stops.indexOf(DeploymentStop.FAILED)
        assertTrue(failed >= 0, "One deployment failed")
        assertEquals(DeploymentStop.DONE, stops[failed + 1], "The next deployment restored production")
        assertEquals(1, stops.count { it == DeploymentStop.CANCELLED }, "One deployment was cancelled")
        assertTrue(slot.deployments.all { it.times != null }, "Every deployment is backdated")
        assertTrue(
            slot.deployments.zipWithNext().all { (a, b) -> !b.times!!.start.isBefore(a.times!!.end) },
            "The deployments follow each other",
        )
        assertTrue(slot.workflows.isEmpty(), "No workflow fires at the reset on a backdated slot")
    }

    @Test
    fun `the seed ends by computing the scorecard of every project`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset)

        val calls = target.journal
        val firstRecompute = calls.indexOfFirst { it.startsWith("recompute") }
        assertTrue(firstRecompute >= 0, "The scorecards are computed")
        assertEquals(
            dataset.projects.map { "recompute ${it.name}" },
            calls.drop(firstRecompute),
            "Nothing is created after the scorecards are computed, and every project is computed",
        )
        assertEquals(
            2,
            calls.take(firstRecompute).count { it.startsWith("estate") },
            "The estates exist before the scorecards are computed",
        )
    }

    /**
     * An estate names labels, and the server refuses to delete a label an estate still names: the
     * reset has to delete the estates first, or the second reset of the demo fails.
     */
    @Test
    fun `the reset deletes the estates before the labels`() {
        val target = InMemoryDemoTarget()
        seed(target).run(dataset)
        val first = target.snapshot()

        seed(target).run(dataset)

        assertEquals(first, target.snapshot())
        assertEquals(2, target.estates().size)
    }

    @Test
    fun `an instance whose licence does not allow the estates is refused before anything is deleted`() {
        val target = InMemoryDemoTarget(scorecardLicensed = false)
        target.createProject("left-over", "A project a visitor created.")

        val error = assertFailsWith<IllegalStateException> { seed(target).run(dataset) }

        assertTrue("Nothing was deleted" in error.message.orEmpty())
        assertTrue(target.projects().any { it.name == "left-over" })
    }

    @Test
    fun `a dataset with no estate does not need the licence`() {
        val target = InMemoryDemoTarget(scorecardLicensed = false)

        seed(target).run(dataset.copy(estates = emptyList()))

        assertTrue(target.estates().isEmpty())
    }

    @Test
    fun `an estate naming what the dataset never creates is caught before anything is deleted`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                dataset.copy(
                    estates = listOf(
                        EstateSpec(
                            name = "Typo",
                            description = "",
                            labels = listOf("portfolio:prodcut"),
                            marker = EstateMarkerSpec.Environment("prod"),
                            readings = listOf(
                                EstateReadingSpec("delivery.leadtime", target = 1.0),
                                EstateReadingSpec(ReadingKeys.DELIVERY_MTTR, windowDays = 0),
                            ),
                        ),
                    ),
                )
            )
        }
        val message = error.message.orEmpty()
        assertTrue("portfolio:prodcut label, which the dataset never creates" in message, message)
        assertTrue("selects no project of the dataset" in message, message)
        assertTrue("the prod environment, which the dataset never creates" in message, message)
        assertTrue("the reading delivery.leadtime, which does not exist" in message, message)
        assertTrue("over a window of 0 days" in message, message)
    }

    @Test
    fun `a test run on a stamp which is not a tests one is caught before anything is deleted`() {
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                singleBuildDataset(
                    stamps = listOf(ValidationStampSpec("PLAIN", ""), ValidationStampSpec("TESTS", "", tests = true)),
                    build = BuildSpec(
                        name = "1",
                        description = "",
                        creation = BuildCreation.DaysAgo(2),
                        validations = listOf(ValidationSpec("TESTS", ValidationStatus.PASSED)),
                        tests = listOf(TestRunSpec("PLAIN", passed = 1)),
                    ),
                )
            )
        }
        val message = error.message.orEmpty()
        assertTrue("TESTS with a status, but it is a tests stamp" in message, message)
        assertTrue("The PLAIN test run of build 1 of one/main is posted on a stamp which is not a tests one" in message, message)
    }

    /**
     * The S4 trap, caught before the reset: the server refuses a pipeline starting before the
     * latest start of its slot, and a deployment at the reset is the latest start there is.
     */
    @Test
    fun `deployments of a slot dated out of order are caught before anything is deleted`() {
        val builds = listOf(
            BuildSpec(name = "1", description = "", creation = BuildCreation.DaysAgo(10)),
            BuildSpec(name = "2", description = "", creation = BuildCreation.DaysAgo(5)),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            seed(InMemoryDemoTarget()).run(
                singleBuildDataset(builds = builds).copy(
                    environments = listOf(
                        EnvironmentSpec(
                            name = "production",
                            order = 100,
                            description = "",
                            slots = listOf(SlotSpec(project = "one", description = "")),
                        ),
                    ),
                    deployments = listOf(
                        // Before its build
                        DeploymentSpec("production", BuildRef("one", "main", "2"), at = BuildCreation.DaysAgo(6)),
                        // Before the one declared before it
                        DeploymentSpec("production", BuildRef("one", "main", "1"), at = BuildCreation.DaysAgo(9)),
                        // At the reset, then dated
                        DeploymentSpec("production", BuildRef("one", "main", "2")),
                        DeploymentSpec("production", BuildRef("one", "main", "2"), at = BuildCreation.DaysAgo(1)),
                    ),
                )
            )
        }
        val message = error.message.orEmpty()
        assertTrue("deployment of 2 on the production/one slot is dated before the creation of the build" in message, message)
        assertTrue("deployment of 1 on the production/one slot is dated before the deployment declared before it" in message, message)
        assertTrue("is dated, and follows a deployment at the reset" in message, message)
    }

    private fun singleBuildDataset(
        stamps: List<ValidationStampSpec> = emptyList(),
        build: BuildSpec? = null,
        builds: List<BuildSpec> = listOfNotNull(build),
    ) = DemoDataset(
        projects = listOf(
            ProjectSpec(
                name = "one",
                description = "",
                branches = listOf(
                    BranchSpec(
                        name = "main",
                        description = "",
                        validationStamps = stamps,
                        builds = builds,
                    ),
                ),
            ),
        ),
    )
}
