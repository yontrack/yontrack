package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.findings.model.*
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Exposure of the findings on branches, their resolution and their acceptance, as maintained by
 * the ingestion of the scans.
 */
@AsAdminTest
class FindingsExposureIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingStateService: FindingStateService

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var testBranchModelMatcherProvider: TestBranchModelMatcherProvider

    private val today: LocalDate get() = Time.now.toLocalDate()

    @Test
    fun `Fixed on main, still exposed on a release branch, the finding stays open in the project`() {
        project {
            withBranchModel()
            val main = branch("main")
            val release = branch("release-x")
            val vsMain = main.findingsStamp()
            val vsRelease = release.findingsStamp()

            val newMain = main.scan(vsMain, entry("CVE-1", severity = "HIGH"))
            assertEquals(listOf(Triple(FindingExposureTransitionType.NEW, "CVE-1", false)), newMain.summary())
            val newRelease = release.scan(vsRelease, entry("CVE-1", severity = "HIGH"))
            assertEquals(listOf(Triple(FindingExposureTransitionType.NEW, "CVE-1", false)), newRelease.summary())
            val finding = finding("CVE-1")
            assertEquals(FindingState.OPEN, state(finding))

            // Fixed on main
            val fixedMain = main.scan(vsMain)
            val resolution = fixedMain.transitions.single()
            assertEquals(FindingExposureTransitionType.RESOLVED, resolution.type)
            assertEquals(finding.id, resolution.finding.id)
            assertEquals(main.id, resolution.branch.id)
            assertEquals(vsMain.id, resolution.validationStamp.id)
            assertEquals(FindingSeverity.HIGH, resolution.severity)
            assertEquals(FindingResolutionReason.ABSENT, resolution.resolutionReason)

            val mainExposure = findingRepository.findExposuresByBranchAndStamp(main.id(), vsMain.id()).single()
            assertEquals(fixedMain.run.runTime, mainExposure.resolvedAt)
            assertEquals(FindingResolutionReason.ABSENT, mainExposure.resolutionReason)
            val releaseExposure = findingRepository.findExposuresByBranchAndStamp(release.id(), vsRelease.id()).single()
            assertNull(releaseExposure.resolvedAt)
            assertEquals(newRelease.run.runTime, releaseExposure.since)

            // Still open in the project
            assertEquals(FindingState.OPEN, state(finding))
            assertNull(finding("CVE-1").resolvedAt)

            // Fixed on the release branch too
            val fixedRelease = release.scan(vsRelease)
            assertEquals(listOf(Triple(FindingExposureTransitionType.RESOLVED, "CVE-1", false)), fixedRelease.summary())
            assertEquals(FindingState.RESOLVED, state(finding))
            assertEquals(fixedRelease.run.runTime, finding("CVE-1").resolvedAt)
            assertEquals(fixedRelease.run.runTime, fixedRelease.transitions.single().finding.resolvedAt)
        }
    }

    @Test
    fun `A branch outside the branch model does not count for the state in the project`() {
        project {
            withBranchModel()
            val main = branch("main")
            val feature = branch("feature-y")
            val vsMain = main.findingsStamp()
            val vsFeature = feature.findingsStamp()

            main.scan(vsMain, entry("CVE-1"))
            val onFeature = feature.scan(vsFeature, entry("CVE-1"))
            // The transitions of any branch are given
            assertEquals(listOf(Triple(FindingExposureTransitionType.NEW, "CVE-1", false)), onFeature.summary())

            // Fixed on main, still exposed on a branch which does not count
            val fixed = main.scan(vsMain)
            assertEquals(FindingState.RESOLVED, state(finding("CVE-1")))
            assertEquals(fixed.run.runTime, finding("CVE-1").resolvedAt)
            assertNull(
                findingRepository.findExposuresByBranchAndStamp(feature.id(), vsFeature.id()).single().resolvedAt
            )
        }
    }

    @Test
    fun `Without a branch model, every branch counts`() {
        project {
            val main = branch("main")
            val feature = branch("feature-y")
            val vsMain = main.findingsStamp()
            val vsFeature = feature.findingsStamp()

            main.scan(vsMain, entry("CVE-1"))
            feature.scan(vsFeature, entry("CVE-1"))
            main.scan(vsMain)
            assertEquals(FindingState.OPEN, state(finding("CVE-1")))
            assertNull(finding("CVE-1").resolvedAt)
        }
    }

    @Test
    fun `The scan of one stamp never resolves the findings of another stamp on the same branch`() {
        project {
            branch {
                val backend = findingsStamp()
                val ui = findingsStamp()

                // Same finding reported by both images, one only by the backend image
                backend.branch.scan(backend, entry("CVE-1"), entry("CVE-2"))
                val uiFirst = scan(ui, entry("CVE-1"))
                // Already exposed on the branch
                assertEquals(emptyList(), uiFirst.transitions)

                // The UI image no longer reports anything
                val uiFixed = scan(ui)
                // Still exposed on the branch through the backend image
                assertEquals(emptyList(), uiFixed.transitions)
                assertNotNull(
                    findingRepository.findExposuresByBranchAndStamp(id(), ui.id()).single().resolvedAt
                )
                val backendExposures = findingRepository.findExposuresByBranchAndStamp(id(), backend.id())
                assertEquals(2, backendExposures.size)
                backendExposures.forEach { assertNull(it.resolvedAt) }
                assertEquals(FindingState.OPEN, state(finding("CVE-1")))
                assertEquals(FindingState.OPEN, state(finding("CVE-2")))

                // The backend image scan without CVE-2 resolves it, not CVE-1
                val backendScan = scan(backend, entry("CVE-1"))
                assertEquals(listOf(Triple(FindingExposureTransitionType.RESOLVED, "CVE-2", false)), backendScan.summary())
                assertEquals(FindingState.OPEN, state(finding("CVE-1")))
                assertEquals(FindingState.RESOLVED, state(finding("CVE-2")))
            }
        }
    }

    @Test
    fun `A finding reappearing after its resolution is exposed again and reopened`() {
        project {
            branch {
                val vs = findingsStamp()
                scan(vs, entry("CVE-1"))
                val resolution = scan(vs)
                assertEquals(listOf(Triple(FindingExposureTransitionType.RESOLVED, "CVE-1", false)), resolution.summary())
                assertEquals(resolution.run.runTime, finding("CVE-1").resolvedAt)

                // Still resolved: nothing changes
                assertEquals(emptyList(), scan(vs).transitions)
                assertEquals(resolution.run.runTime, finding("CVE-1").resolvedAt)

                val reappearance = scan(vs, entry("CVE-1", severity = "CRITICAL"))
                assertEquals(listOf(Triple(FindingExposureTransitionType.NEW, "CVE-1", true)), reappearance.summary())
                assertEquals(FindingSeverity.CRITICAL, reappearance.transitions.single().severity)
                assertEquals(FindingState.OPEN, state(finding("CVE-1")))
                assertNull(finding("CVE-1").resolvedAt)
                val exposure = findingRepository.findExposuresByBranchAndStamp(id(), vs.id()).single()
                assertEquals(reappearance.run.runTime, exposure.since)
                assertNull(exposure.resolvedAt)
                assertNull(exposure.resolutionReason)
            }
        }
    }

    @Test
    fun `An accepted finding is neither open nor resolved, and is not new`() {
        project {
            branch {
                val vs = findingsStamp()
                val first = scan(vs, entry("CVE-1", acceptedUntil = today.plusYears(1)))
                assertEquals(emptyList(), first.transitions)
                val finding = finding("CVE-1")
                assertEquals(FindingState.ACCEPTED, state(finding))
                assertNull(finding.resolvedAt)

                // No longer reported: resolved, without any transition
                val fixed = scan(vs)
                assertEquals(emptyList(), fixed.transitions)
                assertEquals(FindingState.RESOLVED, state(finding))
                assertEquals(fixed.run.runTime, finding("CVE-1").resolvedAt)
            }
        }
    }

    @Test
    fun `An acceptance with a past expiry reads as not accepted, and the next scan without acceptance reopens the finding`() {
        project {
            branch {
                val vs = findingsStamp()
                // Accepted until today
                scan(vs, entry("CVE-1", acceptedUntil = today))
                val finding = finding("CVE-1")
                assertEquals(FindingState.ACCEPTED, findingStateService.getFindingState(finding, today))
                // Read after its expiry, the acceptance no longer counts, without any job
                assertEquals(FindingState.OPEN, findingStateService.getFindingState(finding, today.plusDays(1)))
                assertEquals(
                    mapOf(finding.id to FindingState.OPEN),
                    findingStateService.getFindingStates(project, listOf(finding), today.plusDays(1))
                )

                // Reported without acceptance
                val unaccepted = scan(vs, entry("CVE-1"))
                assertEquals(listOf(Triple(FindingExposureTransitionType.NEW, "CVE-1", true)), unaccepted.summary())
                assertEquals(FindingState.OPEN, state(finding))
            }
        }
    }

    @Test
    fun `A finding reported with an acceptance already expired at scan time is exposed`() {
        project {
            branch {
                val vs = findingsStamp()
                val first = scan(vs, entry("CVE-1", acceptedUntil = today.minusDays(1)))
                assertEquals(listOf(Triple(FindingExposureTransitionType.NEW, "CVE-1", false)), first.summary())
                assertEquals(FindingState.OPEN, state(finding("CVE-1")))
            }
        }
    }

    @Test
    fun `A disabled branch keeps its exposure and is left out of the state in the project`() {
        project {
            val main = branch("main")
            val release = branch("release-x")
            val vsMain = main.findingsStamp()
            val vsRelease = release.findingsStamp()
            main.scan(vsMain, entry("CVE-1"), entry("CVE-2"))
            release.scan(vsRelease, entry("CVE-1"))

            structureService.disableBranch(main)

            // Still exposed on the release branch
            assertEquals(FindingState.OPEN, state(finding("CVE-1")))
            // Only exposed on the disabled branch
            assertEquals(FindingState.RESOLVED, state(finding("CVE-2")))
            // The rows are kept
            assertEquals(2, findingRepository.findExposuresByBranch(main.id()).size)

            structureService.enableBranch(structureService.getBranch(main.id))
            assertEquals(FindingState.OPEN, state(finding("CVE-2")))
        }
    }

    @Test
    fun `A deleted branch takes its exposure with it, silently`() {
        project {
            val main = branch("main")
            val feature = branch("feature-y")
            val vsMain = main.findingsStamp()
            val vsFeature = feature.findingsStamp()
            main.scan(vsMain, entry("CVE-1"))
            feature.scan(vsFeature, entry("CVE-1"), entry("CVE-2"))
            val cve1 = finding("CVE-1")
            val cve2 = finding("CVE-2")

            feature.delete()

            assertEquals(emptyList(), findingRepository.findExposuresByBranch(feature.id()))
            assertEquals(listOf(main.id()), findingRepository.findExposuresByFinding(cve1.id).map { it.branchId })
            assertEquals(emptyList(), findingRepository.findExposuresByFinding(cve2.id))
            assertEquals(FindingState.OPEN, state(cve1))
            // Nothing was fixed: the finding is kept, and is no longer open
            assertEquals(FindingState.RESOLVED, state(cve2))
            assertNull(finding("CVE-2").resolvedAt)

            // The next scan of main is not affected
            assertEquals(emptyList(), main.scan(vsMain, entry("CVE-1")).transitions)
        }
    }

    private fun Project.withBranchModel() {
        testBranchModelMatcherProvider.branchPattern = "main|release-.*"
        testBranchModelMatcherProvider.projects += name
    }

    private fun Branch.findingsStamp(): ValidationStamp =
        validationStamp(
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, vararg entries: String): FindingsIngestionResult =
        findingsIngestionService.ingest(
            build = build(),
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${entries.joinToString(",")}]}"""
                    .parseAsJson(),
            )
        )

    private fun entry(externalId: String, severity: String = "HIGH", acceptedUntil: LocalDate? = null): String {
        val acceptance = acceptedUntil?.let {
            """, "acceptance": {"statement": "Not reachable", "expiresAt": "$it", "source": ".trivyignore.yaml"}"""
        } ?: ""
        return """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "$severity", "title": "Title of $externalId"$acceptance}"""
    }

    private fun Project.finding(externalId: String): Finding =
        findingRepository.findFindingByKey(id(), "trivy", externalId, "pkg:maven/org.x/y")
            ?: error("Finding $externalId not found")

    private fun Branch.finding(externalId: String): Finding = project.finding(externalId)

    private fun state(finding: Finding): FindingState = findingStateService.getFindingState(finding)

    private fun FindingsIngestionResult.summary() =
        transitions.map { Triple(it.type, it.finding.externalId, it.reopened) }
}
