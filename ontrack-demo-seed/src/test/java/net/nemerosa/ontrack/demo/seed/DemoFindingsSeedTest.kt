package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.demo.seed.BuildCreation.DaysAgo
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The security findings of the demo (#1867): what the dataset says about them, and how the seed
 * posts them.
 *
 * The story of a finding - exposed, resolved, accepted - is the server's reading of the scans, not
 * something the in-memory target reproduces. What is pinned here is that the scans the dataset
 * declares tell that story, which is all the seed controls.
 */
class DemoFindingsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private val dataset = DemoContent.dataset(emptyList())

    private val project = dataset.projects.single { it.name == DemoContent.SECURITY }

    private fun branch(name: String) = project.branches.single { it.name == name }

    /** Whether a build's scans of a stamp report a finding. */
    private fun BuildSpec.reports(stamp: String, externalId: String): Boolean =
        scans.filter { it.validationStamp == stamp }.any { scan -> scan.findings.any { it.externalId == externalId } }

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    @Test
    fun `the demo shows a HIGH reported by a few builds of main then fixed there, and still exposed on the release branch`() {
        val main = branch(DemoContent.MAIN).builds
        val reporting = main.takeWhile { it.reports(DemoContent.SECURITY_DEPENDENCIES, DemoContent.CVE_FIXED_ON_MAIN) }
        assertTrue(reporting.size >= 2, "The HIGH is reported by a few builds of main, not one")
        assertTrue(
            main.drop(reporting.size).none { it.reports(DemoContent.SECURITY_DEPENDENCIES, DemoContent.CVE_FIXED_ON_MAIN) },
            "Once fixed on main, it does not come back",
        )
        assertTrue(reporting.size < main.size, "The latest scan of main no longer reports it")

        val release = branch(DemoContent.SECURITY_RELEASE)
        assertTrue(
            release.builds.last().reports(DemoContent.SECURITY_DEPENDENCIES, DemoContent.CVE_FIXED_ON_MAIN),
            "The latest scan of the release branch still reports it",
        )
        // Named as a release branch: the project has no SCM and so no branch model, which makes
        // every branch count for the state of the findings - and the HIGH open for the project
        assertTrue(project.scm == null)
        assertTrue(release.name.startsWith("release-"))

        val finding = main.first().scans.flatMap { it.findings }.single { it.externalId == DemoContent.CVE_FIXED_ON_MAIN }
        assertEquals(FindingSeverity.HIGH, finding.severity)
        assertEquals(null, finding.acceptance)
    }

    @Test
    fun `the demo shows a CRITICAL under an acceptance which has not expired`() {
        project.branches.forEach { branch ->
            val finding = branch.builds.last().scans.flatMap { it.findings }
                .single { it.externalId == DemoContent.CVE_ACCEPTED }
            assertEquals(FindingSeverity.CRITICAL, finding.severity)
            val acceptance = assertNotNull(finding.acceptance, "Accepted on ${branch.name}")
            val days = assertNotNull(acceptance.expiresInDays, "The acceptance on ${branch.name} has an expiry")
            assertTrue(days > 0, "The acceptance on ${branch.name} has not expired")
        }
    }

    @Test
    fun `the expiry of an acceptance follows the day of the reset`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset)

        val report = target.scansOf(DemoContent.SECURITY, DemoContent.MAIN)
            .last { it.spec.validationStamp == DemoContent.SECURITY_DEPENDENCIES }
            .report
        val accepted = report.path("findings").values().single { it.path("externalId").asString() == DemoContent.CVE_ACCEPTED }
        assertEquals(
            LocalDate.of(2026, 9, 1).plusDays(90).toString(),
            accepted.path("acceptance").path("expiresAt").asString(),
        )
    }

    @Test
    fun `one of the scans of the demo is in SARIF, and the CVE is in the neutral format`() {
        val scans = project.branches.flatMap { it.builds }.flatMap { it.scans }
        assertTrue(scans.any { it.format == ScanFormat.SARIF }, "A scan is in SARIF")
        assertTrue(
            scans.filter { scan -> scan.findings.any { it.externalId == DemoContent.CVE_ACCEPTED } }
                .all { it.format == ScanFormat.FINDINGS },
            "The acceptance with an expiry needs the neutral format",
        )
    }

    @Test
    fun `every build of the security project is scanned on both stamps, on both branches`() {
        assertEquals(2, project.branches.size)
        project.branches.forEach { branch ->
            branch.builds.forEach { build ->
                assertEquals(
                    listOf(DemoContent.SECURITY_DEPENDENCIES, DemoContent.SECURITY_CODE),
                    build.scans.map { it.validationStamp },
                    "Scans of ${branch.name}/${build.name}",
                )
            }
        }
    }

    @Test
    fun `the scans are posted with their rendered report, dated between the validations and the promotions`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset)

        val build = target.buildOf(DemoContent.SECURITY, DemoContent.MAIN, "310")
        assertEquals(
            listOf(DemoContent.SECURITY_DEPENDENCIES, DemoContent.SECURITY_CODE),
            build.scans.map { it.spec.validationStamp },
        )
        val validation = build.validations.single().at
        val promotion = build.promotions.single().second
        build.scans.forEach { scan ->
            assertTrue(scan.at.isAfter(validation), "A scan comes after the plain validations")
            assertTrue(scan.at.isBefore(promotion), "A scan comes before the promotions")
        }
        assertEquals(listOf(validation.plusHours(1), validation.plusHours(2)), build.scans.map { it.at })
        assertEquals("2.1.0", build.scans.last().report.path("version").asString())
        assertEquals("trivy", build.scans.first().report.path("scanner").asString())
    }

    @Test
    fun `the release branch is scanned before main, since its builds are the oldest`() {
        val target = InMemoryDemoTarget()

        seed(target).run(dataset)

        val project = target.projects().single { it.name == DemoContent.SECURITY } as InMemoryDemoTarget.InMemoryProject
        assertEquals(listOf(DemoContent.SECURITY_RELEASE, DemoContent.MAIN), project.branches.map { it.name })
        val oldestRelease = project.branches.first().builds.first().creation
        val oldestMain = project.branches.last().builds.first().creation
        assertTrue(oldestRelease.isBefore(oldestMain))
    }

    @Test
    fun `an instance whose licence does not allow SARIF is refused before anything is deleted`() {
        val target = InMemoryDemoTarget(nativeFindingsFormats = false)
        target.createProject("left-over", "A project a visitor created.")

        val ex = assertFailsWith<IllegalStateException> {
            seed(target).run(dataset)
        }

        assertTrue("Nothing was deleted" in ex.message!!)
        assertEquals(listOf("left-over"), target.projects().map { it.name })
    }

    @Test
    fun `a dataset posting no SARIF scan does not need the licence`() {
        val target = InMemoryDemoTarget(nativeFindingsFormats = false)

        seed(target).run(scannedDataset(ScanFormat.FINDINGS))

        assertEquals(1, target.scansOf("scanned", DemoContent.MAIN).size)
    }

    @Test
    fun `a SARIF scan declaring the expiry of an acceptance is refused before anything is deleted`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            scannedDataset(
                ScanFormat.SARIF,
                acceptance = AcceptanceSpec("Tolerated", "suppressions.sarif", expiresInDays = 10),
            ).validate()
        }
        assertTrue("SARIF, which has no field for the expiry" in ex.message!!, ex.message)
    }

    @Test
    fun `a scan on a stamp which is not a security-findings one is refused before anything is deleted`() {
        val dataset = scannedDataset(ScanFormat.FINDINGS, findingsStamp = false)
        val ex = assertFailsWith<IllegalArgumentException> { dataset.validate() }
        assertTrue("not a security-findings one" in ex.message!!, ex.message)
    }

    @Test
    fun `a status on a security-findings stamp is refused before anything is deleted`() {
        val dataset = scannedDataset(ScanFormat.FINDINGS).let { dataset ->
            dataset.copy(projects = dataset.projects.map { project ->
                project.copy(branches = project.branches.map { branch ->
                    branch.copy(builds = branch.builds.map {
                        it.copy(validations = listOf(ValidationSpec("SCAN", ValidationStatus.PASSED)))
                    })
                })
            })
        }
        val ex = assertFailsWith<IllegalArgumentException> { dataset.validate() }
        assertTrue("declare a scan instead" in ex.message!!, ex.message)
    }

    @Test
    fun `a threshold at UNKNOWN is refused before anything is deleted`() {
        val dataset = scannedDataset(
            ScanFormat.FINDINGS,
            thresholds = FindingsThresholdsSpec(warningLevel = FindingSeverity.UNKNOWN),
        )
        val ex = assertFailsWith<IllegalArgumentException> { dataset.validate() }
        assertTrue("threshold at UNKNOWN" in ex.message!!, ex.message)
    }

    /**
     * A dataset of one project, one branch and one build scanned once on a stamp `SCAN`.
     */
    private fun scannedDataset(
        format: ScanFormat,
        acceptance: AcceptanceSpec? = null,
        findingsStamp: Boolean = true,
        thresholds: FindingsThresholdsSpec = FindingsThresholdsSpec(),
    ) = DemoDataset(
        projects = listOf(
            ProjectSpec(
                name = "scanned",
                description = "Scanned",
                branches = listOf(
                    BranchSpec(
                        name = DemoContent.MAIN,
                        description = "Main",
                        validationStamps = listOf(
                            ValidationStampSpec("SCAN", "Scan", findings = thresholds.takeIf { findingsStamp }),
                        ),
                        builds = listOf(
                            BuildSpec(
                                name = "1",
                                description = "Scanned build",
                                creation = DaysAgo(1),
                                scans = listOf(
                                    ScanSpec(
                                        validationStamp = "SCAN",
                                        format = format,
                                        kind = ScanKind.CODE,
                                        scanner = "codeql",
                                        findings = listOf(
                                            FindingSpec(
                                                externalId = "java/rule",
                                                location = "src/Main.java",
                                                severity = FindingSeverity.LOW,
                                                title = "A rule",
                                                acceptance = acceptance,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun InMemoryDemoTarget.buildOf(project: String, branch: String, build: String) =
        (projects().single { it.name == project } as InMemoryDemoTarget.InMemoryProject)
            .branches.single { it.name == branch }
            .builds.single { it.name == build }

    private fun InMemoryDemoTarget.scansOf(project: String, branch: String) =
        (projects().single { it.name == project } as InMemoryDemoTarget.InMemoryProject)
            .branches.single { it.name == branch }
            .builds.flatMap { it.scans }
}
