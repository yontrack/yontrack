package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The security readings of the demo's scorecard and the findings fan-out of its estate view (#1912).
 *
 * The readings are computed by the server, from the scans the seed posts: what is pinned here is that
 * the estates and the scans of the dataset are what make each reading show something - several rungs
 * of maturity, resolved findings for the remediation time, an overdue finding, accepted ones, and one
 * finding reported by several projects of an estate.
 */
class DemoSecurityReadingsSeedTest {

    private val now = LocalDateTime.of(2026, 9, 1, 10, 15, 30)
    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private val dataset = DemoContent.dataset(emptyList())

    private fun estate(name: String) = dataset.estates.single { it.name == name }

    private fun project(name: String) = dataset.projects.single { it.name == name }

    private fun projectsOf(estate: String): List<ProjectSpec> {
        val spec = estate(estate)
        return dataset.projects.filter { it.labels.containsAll(spec.labels) }
    }

    /** Scans of a project, with the build which posted them, oldest build first on each branch. */
    private fun ProjectSpec.scans(): List<Pair<BuildSpec, ScanSpec>> =
        branches.flatMap { branch -> branch.builds.flatMap { build -> build.scans.map { build to it } } }

    private fun BuildSpec.findings(): List<FindingSpec> = scans.flatMap { it.findings }

    private fun BuildSpec.reports(externalId: String) = findings().any { it.externalId == externalId }

    private fun BuildSpec.age(): Duration = Duration.between(creation.resolve(now), now)

    @Test
    fun `the estates expect kinds of scan and set remediation targets`() {
        val products = estate(DemoContent.ESTATE_PRODUCTS).security
        assertEquals(listOf(ScanKind.DEPENDENCIES, ScanKind.CODE), products.expectedKinds)
        assertEquals(7, products.freshnessDays)
        assertNotNull(products.criticalTargetDays)
        assertNotNull(products.highTargetDays)

        val production = estate(DemoContent.ESTATE_PRODUCTION).security
        assertEquals(listOf(ScanKind.DEPENDENCIES), production.expectedKinds)
        assertNull(production.freshnessDays, "The freshness of the settings")
        assertNotNull(production.criticalTargetDays)
        assertNotNull(production.highTargetDays)
    }

    @Test
    fun `the estates judge the security readings`() {
        val products = estate(DemoContent.ESTATE_PRODUCTS).readings.associateBy { it.key }
        listOf(
            ReadingKeys.SECURITY_MATURITY,
            ReadingKeys.SECURITY_REMEDIATION_TIME,
            ReadingKeys.SECURITY_OVERDUE,
        ).forEach { key ->
            assertNotNull(products[key]?.target, "Demo products judges $key")
        }
        val production = estate(DemoContent.ESTATE_PRODUCTION).readings.associateBy { it.key }
        assertNotNull(production[ReadingKeys.SECURITY_MATURITY]?.target)
        assertNull(production[ReadingKeys.SECURITY_OVERDUE], "Demo production shows its overdue findings without a verdict")
    }

    /**
     * Covered in "Demo products" means every expected kind scanned within the freshness: one project
     * scans both, one only its dependencies, and one nothing at all - three different rungs.
     */
    @Test
    fun `the projects of the products estate cover its expected kinds to different extents`() {
        val security = estate(DemoContent.ESTATE_PRODUCTS).security
        val freshness = Duration.ofDays(security.freshnessDays!!.toLong())
        val freshKinds = projectsOf(DemoContent.ESTATE_PRODUCTS).associate { project ->
            project.name to project.scans()
                .filter { (build, _) -> build.age() < freshness }
                .map { (_, scan) -> scan.kind }
                .toSet()
        }

        assertTrue(freshKinds.getValue(DemoContent.SECURITY).containsAll(security.expectedKinds), "Covered")
        assertEquals(setOf(ScanKind.DEPENDENCIES), freshKinds.getValue(DemoContent.VISITS), "Reported, not covered")
        assertEquals(emptySet(), freshKinds.getValue(DemoContent.SERVICE), "No scan at all")
        // ... and covered in "Demo production", which expects the dependencies only
        assertTrue(
            freshKinds.getValue(DemoContent.VISITS).containsAll(estate(DemoContent.ESTATE_PRODUCTION).security.expectedKinds),
        )
    }

    /** Gating: a scan created FAILED in the window, by a CRITICAL nobody accepted. */
    @Test
    fun `the security project has a scan failing on an unaccepted CRITICAL, and is not promoted`() {
        val failing = project(DemoContent.SECURITY).branches.flatMap { it.builds }.filter { build ->
            build.findings().any { it.severity == FindingSeverity.CRITICAL && it.acceptance == null }
        }
        assertEquals(listOf("311"), failing.map { it.name })
        assertTrue(failing.single().promotionLevels.isEmpty(), "A build failing its scan is not promoted")
        assertTrue(failing.single().reports(DemoContent.CVE_CRITICAL_FIXED))
    }

    /**
     * Resolved for the project: reported on one branch only, and no longer reported by the build which
     * follows. Two of them, a CRITICAL and a HIGH, of different durations, so that the remediation time
     * is a median.
     */
    @Test
    fun `the security project resolves a CRITICAL and a HIGH`() {
        val project = project(DemoContent.SECURITY)
        val durations = listOf(DemoContent.CVE_CRITICAL_FIXED, DemoContent.CVE_HIGH_FIXED).map { cve ->
            val reporting = project.branches.filter { branch -> branch.builds.any { it.reports(cve) } }
            val branch = reporting.single()
            val first = branch.builds.indexOfFirst { it.reports(cve) }
            val fixed = branch.builds.drop(first).indexOfFirst { !it.reports(cve) } + first
            assertTrue(fixed > first, "$cve is fixed")
            assertTrue(branch.builds.drop(fixed).none { it.reports(cve) }, "$cve does not come back")
            Duration.between(branch.builds[first].creation.resolve(now), branch.builds[fixed].creation.resolve(now))
        }
        assertEquals(listOf(3L, 11L), durations.map { it.toDays() })
        val target = estate(DemoContent.ESTATE_PRODUCTS).readings
            .single { it.key == ReadingKeys.SECURITY_REMEDIATION_TIME }.target!!
        assertTrue(Duration.ofDays(7).seconds <= target, "The median of seven days meets the target")
    }

    @Test
    fun `the security project has a HIGH open past the HIGH target of the products estate`() {
        val release = project(DemoContent.SECURITY).branches.single { it.name == DemoContent.SECURITY_RELEASE }
        assertTrue(release.builds.last().reports(DemoContent.CVE_FIXED_ON_MAIN), "Still open")
        val firstSeen = release.builds.first { it.reports(DemoContent.CVE_FIXED_ON_MAIN) }
        val target = estate(DemoContent.ESTATE_PRODUCTS).security.highTargetDays!!
        assertTrue(firstSeen.age().toDays() > target, "Overdue: ${firstSeen.age().toDays()} days")
        // The only one: the CRITICAL fixed on main was open for less than its target
        val critical = estate(DemoContent.ESTATE_PRODUCTS).security.criticalTargetDays!!
        assertTrue(3 < critical)
    }

    @Test
    fun `the latest scans of the security project carry accepted CRITICAL and HIGH findings`() {
        val accepted = project(DemoContent.SECURITY).branches.flatMap { branch ->
            branch.builds.last().findings().filter { it.acceptance != null }
        }.map { it.severity }.toSet()
        assertEquals(setOf(FindingSeverity.CRITICAL, FindingSeverity.HIGH), accepted)
    }

    /**
     * The fan-out of "Demo products": one CVE reported by two of its projects - still exposed on a
     * branch of one and resolved on its other branch, accepted in the other project at one location
     * and resolved there at another.
     */
    @Test
    fun `the fan-out shows a CVE open, resolved and accepted across the projects of an estate`() {
        val cve = DemoContent.CVE_FIXED_ON_MAIN
        val reporting = projectsOf(DemoContent.ESTATE_PRODUCTS).filter { project ->
            project.branches.any { branch -> branch.builds.any { it.reports(cve) } }
        }.map { it.name }
        assertEquals(listOf(DemoContent.SECURITY, DemoContent.VISITS), reporting)

        val security = project(DemoContent.SECURITY).branches.associateBy { it.name }
        assertTrue(security.getValue(DemoContent.SECURITY_RELEASE).builds.last().reports(cve), "Exposed")
        assertTrue(security.getValue(DemoContent.MAIN).builds.first().reports(cve))
        assertTrue(!security.getValue(DemoContent.MAIN).builds.last().reports(cve), "Resolved on a branch")

        val visits = project(DemoContent.VISITS).branches.single().builds.filter { it.reports(cve) }
        val byLocation = visits.flatMap { build -> build.findings().filter { it.externalId == cve }.map { build to it } }
            .groupBy({ it.second.location }, { it.first })
        assertEquals(2, byLocation.size, "Two locations in the same project")
        val (accepted, fixed) = byLocation.entries.partition { (location, _) ->
            visits.flatMap { it.findings() }.first { it.externalId == cve && it.location == location }.acceptance != null
        }
        val acceptedBuilds = accepted.single().value
        assertEquals(project(DemoContent.VISITS).branches.single().builds.last(), acceptedBuilds.last(), "Still accepted")
        acceptedBuilds.forEach { build ->
            val acceptance = build.findings().single { it.externalId == cve && it.location == accepted.single().key }.acceptance
            assertTrue((acceptance?.expiresInDays ?: 0) > 0, "Under an acceptance which has not expired")
        }
        val fixedBuilds = fixed.single().value
        assertTrue(fixedBuilds.last() != visits.last(), "Resolved for the project")
    }

    /**
     * The other project of "Demo products" with a remediation time, and one missing the target:
     * the HIGH it fixed by a bump stayed open fourteen days.
     */
    @Test
    fun `the scorecard project resolves a HIGH slower than the target of the products estate`() {
        val builds = project(DemoContent.VISITS).branches.single().builds
        val location = "pkg:maven/org.springframework/spring-webmvc"
        fun BuildSpec.reportsWebMvc() = findings().any { it.externalId == DemoContent.CVE_FIXED_ON_MAIN && it.location == location }
        val first = builds.first { it.reportsWebMvc() }
        val fixed = builds.dropWhile { !it.reportsWebMvc() }.first { !it.reportsWebMvc() }
        assertTrue(builds.dropWhile { it != fixed }.none { it.reportsWebMvc() }, "It does not come back")
        val days = Duration.between(first.creation.resolve(now), fixed.creation.resolve(now)).toDays()
        assertEquals(14, days)
        val target = estate(DemoContent.ESTATE_PRODUCTS).readings
            .single { it.key == ReadingKeys.SECURITY_REMEDIATION_TIME }.target!!
        assertTrue(Duration.ofDays(days).seconds > target, "Missed")
    }

    @Test
    fun `the estates are created with their security fields`() {
        val target = InMemoryDemoTarget()
        DemoSeed(target, clock, log = {}).run(dataset)

        val snapshot = target.snapshot()
        dataset.estates.forEach { estate ->
            assertTrue("  security ${estate.security}" in snapshot, "Security of ${estate.name}")
        }
    }

    @Test
    fun `security fields the server would refuse are caught before anything is deleted`() {
        val estate = estate(DemoContent.ESTATE_PRODUCTS)
        val error = assertFailsWith<IllegalArgumentException> {
            dataset.copy(
                estates = listOf(
                    estate.copy(
                        readings = estate.readings + EstateReadingSpec("security.overdu", target = 0.0),
                        security = EstateSecuritySpec(
                            expectedKinds = listOf(ScanKind.CODE, ScanKind.CODE),
                            freshnessDays = 0,
                            criticalTargetDays = -1,
                            highTargetDays = -2,
                        ),
                    ),
                ),
            ).validate()
        }
        val message = error.message.orEmpty()
        assertTrue("the reading security.overdu, which does not exist" in message, message)
        assertTrue("keeps its security scans fresh for 0 days" in message, message)
        assertTrue("gives its CRITICAL findings a target of -1 days" in message, message)
        assertTrue("gives its HIGH findings a target of -2 days" in message, message)
        assertTrue("expects the same kind of scan more than once" in message, message)
    }
}
