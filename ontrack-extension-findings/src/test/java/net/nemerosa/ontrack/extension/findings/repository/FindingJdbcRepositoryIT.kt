package net.nemerosa.ontrack.extension.findings.repository

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.*
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@AsAdminTest
class FindingJdbcRepositoryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingRepository: FindingRepository

    private val t0: LocalDateTime = Time.truncate(Time.now().minusDays(10))
    private val t1: LocalDateTime = t0.plusDays(1)

    @Test
    fun `Inserting a finding and reading it back by ID and by key`() {
        val project = doCreateProject()
        val finding = findingRepository.insertFinding(
            finding(
                project,
                externalId = "CVE-2021-44228",
                location = "pkg:maven/org.apache.logging.log4j/log4j-core",
                url = "https://avd.aquasec.com/nvd/cve-2021-44228",
                resolvedAt = t1,
            )
        )
        assertTrue(finding.id > 0, "ID is assigned")

        assertEquals(finding, findingRepository.findFindingById(finding.id))
        assertEquals(
            finding,
            findingRepository.findFindingByKey(
                projectId = project.id(),
                scanner = "trivy",
                externalId = "CVE-2021-44228",
                location = "pkg:maven/org.apache.logging.log4j/log4j-core",
            )
        )
        assertNull(
            findingRepository.findFindingByKey(
                projectId = project.id(),
                scanner = "trivy",
                externalId = "CVE-2021-44228",
                location = "pkg:maven/org.apache.logging.log4j/log4j-api",
            )
        )
    }

    @Test
    fun `An empty location is part of the key`() {
        val project = doCreateProject()
        val finding = findingRepository.insertFinding(
            finding(project, scanner = "zap", externalId = "10038", location = "", kind = FindingKind.DAST)
        )
        assertEquals(finding, findingRepository.findFindingByKey(project.id(), "zap", "10038", ""))
    }

    @Test
    fun `The key of a finding is unique in a project`() {
        val project = doCreateProject()
        findingRepository.insertFinding(finding(project, externalId = "CVE-1"))
        assertThrows<DuplicateKeyException> {
            findingRepository.insertFinding(finding(project, externalId = "CVE-1", title = "Another title"))
        }
    }

    @Test
    fun `The same key in two projects gives two findings`() {
        val p1 = doCreateProject()
        val p2 = doCreateProject()
        val f1 = findingRepository.insertFinding(finding(p1, externalId = "CVE-2"))
        val f2 = findingRepository.insertFinding(finding(p2, externalId = "CVE-2"))
        assertTrue(f1.id != f2.id)
        assertEquals(listOf(f1), findingRepository.findFindingsByProject(p1.id()))
        assertEquals(listOf(f2), findingRepository.findFindingsByProject(p2.id()))
        assertEquals(
            setOf(f1, f2),
            findingRepository.findFindingsByExternalId("CVE-2").filter { it.id in setOf(f1.id, f2.id) }.toSet()
        )
    }

    @Test
    fun `Updating a finding`() {
        val project = doCreateProject()
        val finding = findingRepository.insertFinding(finding(project, externalId = "CVE-3"))
        val updated = finding.copy(
            title = "New title",
            url = "https://example.com/CVE-3",
            lastSeen = t1,
            resolvedAt = t1,
            maxSeverity = FindingSeverity.CRITICAL,
        )
        findingRepository.updateFinding(updated)
        assertEquals(updated, findingRepository.findFindingById(finding.id))
    }

    @Test
    fun `Observations are inserted as a batch and read back by finding and by run`() {
        project {
            branch {
                val vs = validationStamp()
                val run1 = build().validate(vs)
                val run2 = build().validate(vs)
                val f1 = findingRepository.insertFinding(finding(project, externalId = "CVE-10"))
                val f2 = findingRepository.insertFinding(finding(project, externalId = "CVE-11"))

                val o11 = observation(
                    f1, run1, t0,
                    installedVersion = "1.2.3",
                    fixedVersion = "1.2.4",
                    acceptance = FindingAcceptance(
                        statement = "Not reachable",
                        expiresAt = LocalDate.of(2026, 12, 31),
                        source = "security/dast/suppressions.yaml",
                    )
                )
                val o21 = observation(f2, run1, t0, severity = FindingSeverity.UNKNOWN, rawSeverity = null)
                val o12 = observation(
                    f1, run2, t1,
                    severity = FindingSeverity.CRITICAL,
                    acceptance = FindingAcceptance(statement = null, expiresAt = null, source = null),
                )
                findingRepository.insertObservations(listOf(o11, o21, o12))

                assertEquals(listOf(o12, o11), findingRepository.findObservationsByFinding(f1.id))
                assertEquals(listOf(o21), findingRepository.findObservationsByFinding(f2.id))
                assertEquals(
                    setOf(o11, o21),
                    findingRepository.findObservationsByValidationRun(run1.id()).toSet()
                )
                assertEquals(listOf(o12), findingRepository.findObservationsByValidationRun(run2.id()))
            }
        }
    }

    @Test
    fun `Inserting no observation`() {
        findingRepository.insertObservations(emptyList())
    }

    @Test
    fun `A run observes a finding only once`() {
        project {
            branch {
                val vs = validationStamp()
                val run = build().validate(vs)
                val f = findingRepository.insertFinding(finding(project, externalId = "CVE-12"))
                findingRepository.insertObservations(listOf(observation(f, run, t0)))
                assertThrows<DuplicateKeyException> {
                    findingRepository.insertObservations(listOf(observation(f, run, t1)))
                }
            }
        }
    }

    @Test
    fun `Exposures are saved as a batch, read back and replaced`() {
        project {
            val main = branch()
            val release = branch()
            val vsMain = main.validationStamp()
            val vsMainOther = main.validationStamp()
            val vsRelease = release.validationStamp()
            val f1 = findingRepository.insertFinding(finding(this, externalId = "CVE-20"))
            val f2 = findingRepository.insertFinding(finding(this, externalId = "CVE-21"))

            val e1Main = FindingExposure(f1.id, main.id(), vsMain.id(), t0)
            val e2Main = FindingExposure(
                f2.id, main.id(), vsMain.id(), t1,
                accepted = true,
                acceptanceExpiresAt = LocalDate.of(2026, 12, 31),
            )
            val e1MainOther = FindingExposure(f1.id, main.id(), vsMainOther.id(), t1)
            val e1Release = FindingExposure(f1.id, release.id(), vsRelease.id(), t1)
            findingRepository.saveExposures(listOf(e1Main, e2Main, e1MainOther, e1Release))

            assertEquals(setOf(e1Main, e1MainOther, e1Release), findingRepository.findExposuresByFinding(f1.id).toSet())
            assertEquals(
                setOf(e1Main, e2Main, e1MainOther, e1Release),
                findingRepository.findExposuresByFindings(listOf(f1.id, f2.id)).toSet()
            )
            assertEquals(emptyList(), findingRepository.findExposuresByFindings(emptyList()))
            assertEquals(
                setOf(e1Main, e2Main, e1MainOther),
                findingRepository.findExposuresByBranch(main.id()).toSet()
            )
            assertEquals(
                setOf(e1Main, e2Main),
                findingRepository.findExposuresByBranchAndStamp(main.id(), vsMain.id()).toSet()
            )

            // Resolution replaces the row
            val e1MainResolved = e1Main.copy(resolvedAt = t1, resolutionReason = FindingResolutionReason.ABSENT)
            findingRepository.saveExposures(listOf(e1MainResolved))
            assertEquals(
                setOf(e1MainResolved, e2Main),
                findingRepository.findExposuresByBranchAndStamp(main.id(), vsMain.id()).toSet()
            )
            assertEquals(setOf(e1MainResolved, e1MainOther, e1Release), findingRepository.findExposuresByFinding(f1.id).toSet())
        }
    }

    @Test
    fun `Saving no exposure`() {
        findingRepository.saveExposures(emptyList())
    }

    @Test
    fun `Latest severities of findings by the runs of a stamp`() {
        project {
            branch {
                val vs = validationStamp()
                val other = validationStamp()
                val run1 = build().validate(vs)
                val run2 = build().validate(vs)
                val runOther = build().validate(other)
                val f1 = findingRepository.insertFinding(finding(project, externalId = "CVE-25"))
                val f2 = findingRepository.insertFinding(finding(project, externalId = "CVE-26"))
                val f3 = findingRepository.insertFinding(finding(project, externalId = "CVE-27"))
                findingRepository.insertObservations(
                    listOf(
                        observation(f1, run1, t0).copy(severity = FindingSeverity.HIGH),
                        observation(f1, run2, t1).copy(severity = FindingSeverity.LOW),
                        observation(f2, run1, t0).copy(severity = FindingSeverity.MEDIUM),
                        observation(f3, runOther, t1).copy(severity = FindingSeverity.CRITICAL),
                    )
                )
                assertEquals(
                    mapOf(f1.id to FindingSeverity.LOW, f2.id to FindingSeverity.MEDIUM),
                    findingRepository.findLatestSeverities(vs.id(), listOf(f1.id, f2.id, f3.id))
                )
                assertEquals(emptyMap(), findingRepository.findLatestSeverities(vs.id(), emptyList()))
            }
        }
    }

    @Test
    fun `Getting findings by IDs`() {
        project {
            val f1 = findingRepository.insertFinding(finding(this, externalId = "CVE-28"))
            val f2 = findingRepository.insertFinding(finding(this, externalId = "CVE-29"))
            findingRepository.insertFinding(finding(this, externalId = "CVE-2A"))
            assertEquals(listOf(f1, f2), findingRepository.findFindingsByIds(listOf(f2.id, f1.id)))
            assertEquals(emptyList(), findingRepository.findFindingsByIds(emptyList()))
        }
    }

    @Test
    fun `Deleting a validation run deletes its observations but keeps the finding`() {
        project {
            branch {
                val vs = validationStamp()
                val run1 = build().validate(vs)
                val run2 = build().validate(vs)
                val f = findingRepository.insertFinding(finding(project, externalId = "CVE-30"))
                val o1 = observation(f, run1, t0)
                val o2 = observation(f, run2, t1)
                findingRepository.insertObservations(listOf(o1, o2))

                asAdmin { structureService.deleteValidationRun(run1) }

                assertEquals(emptyList(), findingRepository.findObservationsByValidationRun(run1.id()))
                assertEquals(listOf(o2), findingRepository.findObservationsByFinding(f.id))
                assertEquals(f, findingRepository.findFindingById(f.id))
            }
        }
    }

    @Test
    fun `Deleting a branch deletes its exposure and its observations but keeps the finding`() {
        project {
            val main = branch()
            val feature = branch()
            val vsMain = main.validationStamp()
            val vsFeature = feature.validationStamp()
            val runMain = main.build().validate(vsMain)
            val runFeature = feature.build().validate(vsFeature)
            val f = findingRepository.insertFinding(finding(this, externalId = "CVE-40"))
            val oMain = observation(f, runMain, t0)
            findingRepository.insertObservations(listOf(oMain, observation(f, runFeature, t1)))
            val eMain = FindingExposure(f.id, main.id(), vsMain.id(), t0)
            findingRepository.saveExposures(
                listOf(eMain, FindingExposure(f.id, feature.id(), vsFeature.id(), t1))
            )

            feature.delete()

            assertEquals(listOf(eMain), findingRepository.findExposuresByFinding(f.id))
            assertEquals(emptyList(), findingRepository.findExposuresByBranchAndStamp(feature.id(), vsFeature.id()))
            assertEquals(listOf(oMain), findingRepository.findObservationsByFinding(f.id))
            assertEquals(f, findingRepository.findFindingById(f.id))
        }
    }

    @Test
    fun `Findings survive a build purge`() {
        project {
            branch {
                val vs = validationStamp()
                val builds = (1..3).map { build() }
                val runs = builds.map { it.validate(vs) }
                val f = findingRepository.insertFinding(
                    finding(project, externalId = "CVE-50", lastSeen = t1, maxSeverity = FindingSeverity.HIGH)
                )
                findingRepository.insertObservations(runs.map { observation(f, it, t0) })
                findingRepository.saveExposures(listOf(FindingExposure(f.id, id(), vs.id(), t0)))

                // Purging all the builds
                builds.forEach { it.delete() }

                runs.forEach { run ->
                    assertEquals(emptyList(), findingRepository.findObservationsByValidationRun(run.id()))
                }
                assertEquals(emptyList(), findingRepository.findObservationsByFinding(f.id))
                val kept = findingRepository.findFindingById(f.id)
                assertNotNull(kept, "The finding survives the purge of its builds") {
                    assertEquals(f, it)
                    assertEquals(t0, it.firstSeen)
                    assertEquals(t1, it.lastSeen)
                    assertEquals(FindingSeverity.HIGH, it.maxSeverity)
                }
                // The exposure is not about builds
                assertEquals(1, findingRepository.findExposuresByFinding(f.id).size)
            }
        }
    }

    @Test
    fun `Deleting a project deletes its findings`() {
        val project = doCreateProject()
        val f = findingRepository.insertFinding(finding(project, externalId = "CVE-60"))
        asAdmin { structureService.deleteProject(project.id) }
        assertNull(findingRepository.findFindingById(f.id))
    }

    private fun finding(
        project: Project,
        scanner: String = "trivy",
        externalId: String,
        location: String = "pkg:maven/org.example/lib",
        kind: FindingKind = FindingKind.IMAGE,
        title: String = "Title of $externalId",
        url: String? = null,
        lastSeen: LocalDateTime = t0,
        resolvedAt: LocalDateTime? = null,
        maxSeverity: FindingSeverity = FindingSeverity.MEDIUM,
    ) = Finding(
        id = 0,
        projectId = project.id(),
        scanner = scanner,
        externalId = externalId,
        location = location,
        kind = kind,
        title = title,
        url = url,
        firstSeen = t0,
        lastSeen = lastSeen,
        resolvedAt = resolvedAt,
        maxSeverity = maxSeverity,
    )

    private fun observation(
        finding: Finding,
        run: ValidationRun,
        time: LocalDateTime,
        severity: FindingSeverity = FindingSeverity.HIGH,
        rawSeverity: String? = "HIGH (nvd)",
        installedVersion: String? = null,
        fixedVersion: String? = null,
        acceptance: FindingAcceptance? = null,
    ) = FindingObservation(
        findingId = finding.id,
        validationRunId = run.id(),
        time = time,
        severity = severity,
        rawSeverity = rawSeverity,
        installedVersion = installedVersion,
        fixedVersion = fixedVersion,
        acceptance = acceptance,
    )
}
