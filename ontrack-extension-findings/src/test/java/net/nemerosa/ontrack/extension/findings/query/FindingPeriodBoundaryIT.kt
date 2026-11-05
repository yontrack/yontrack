package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionRequest
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionResult
import net.nemerosa.ontrack.extension.findings.ingestion.FindingsIngestionService
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundary
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundaryStatus
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The boundary of a period, across discover, fix and reopen.
 */
@AsAdminTest
class FindingPeriodBoundaryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingsIngestionService: FindingsIngestionService

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Autowired
    private lateinit var findingRepository: FindingRepository

    @Autowired
    private lateinit var findingQueryService: FindingQueryService

    @Autowired
    private lateinit var findingPeriodBoundaryService: FindingPeriodBoundaryService

    private val gold = StandardBuildFilterData.of(10).withWithPromotionLevel("GOLD")

    @Test
    fun `A later promoted build is the boundary, with the time of its promotion`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                scan(vs)
                build() // Not promoted
                val promoted = build()
                promoted.promote(level, time = Time.now.plusHours(1))

                val boundary = project.boundaries("CVE-1", gold).single()!!
                assertEquals(FindingPeriodBoundaryStatus.MATCHED, boundary.status)
                assertEquals(promoted.id, boundary.build?.id)
                assertEquals(structureService.getPromotionRunsForBuild(promoted.id).single().signature.time, boundary.time)
                assertNotNull(boundary.durationSeconds)
            }
        }
    }

    @Test
    fun `A count of one does not lose the matching build`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                scan(vs)
                val promoted = build()
                promoted.promote(level)
                // More recent than the boundary
                build().promote(level)
                build().promote(level)

                val boundary = project.boundaries("CVE-1", gold.copy(count = 1)).single()!!
                assertEquals(promoted.id, boundary.build?.id)
            }
        }
    }

    @Test
    fun `An older build promoted after the fix still contains the finding and does not count`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                val discovered = scan(vs, "CVE-1")
                scan(vs)
                discovered.build.promote(level)

                val boundary = project.boundaries("CVE-1", gold).single()!!
                assertEquals(FindingPeriodBoundaryStatus.NONE_YET, boundary.status)
                assertNull(boundary.build)
                assertNotNull(boundary.durationSeconds)
            }
        }
    }

    @Test
    fun `A matching build which was never scanned counts`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                scan(vs)
                val neverScanned = build()
                neverScanned.promote(level)

                assertEquals(neverScanned.id, project.boundaries("CVE-1", gold).single()?.build?.id)
            }
        }
    }

    @Test
    fun `The fixing build itself can be the boundary, never before the scan`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                val fix = build()
                // Promoted before the scan was posted
                fix.promote(level, time = Time.now.minusDays(2))
                val fixed = scan(fix, vs)

                val boundary = project.boundaries("CVE-1", gold).single()!!
                assertEquals(FindingPeriodBoundaryStatus.MATCHED, boundary.status)
                assertEquals(fix.id, boundary.build?.id)
                assertEquals(fixed.run.runTime, boundary.time)
            }
        }
    }

    @Test
    fun `Reopened before any matching build is not reached, and the next period bounds the search`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                scan(vs)
                // Reported again, the promotion is on a build which contains it
                val reopened = scan(vs, "CVE-1")
                reopened.build.promote(level)

                val (first, second) = project.boundaries("CVE-1", gold)
                assertEquals(FindingPeriodBoundaryStatus.NOT_REACHED, first?.status)
                assertNull(first?.durationSeconds)
                assertNull(second, "The period which is still open has no boundary")
            }
        }
    }

    @Test
    fun `Each period ended has its own boundary`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                val firstFix = scan(vs)
                firstFix.build.promote(level)
                scan(vs, "CVE-1")
                scan(vs)
                val secondFix = build()
                secondFix.promote(level)

                val (first, second) = project.boundaries("CVE-1", gold)
                assertEquals(firstFix.build.id, first?.build?.id)
                assertEquals(secondFix.id, second?.build?.id)
            }
        }
    }

    @Test
    fun `The time of a validation is the first run of the stamp with the status`() {
        project {
            branch {
                val vs = findingsStamp()
                val other = validationStamp("TESTS")
                scan(vs, "CVE-1")
                scan(vs)
                val validated = build()
                validated.validate(other, ValidationRunStatusID.STATUS_FAILED)
                val passed = validated.validate(other, ValidationRunStatusID.STATUS_PASSED)

                val boundary = project.boundaries(
                    "CVE-1",
                    StandardBuildFilterData.of(10)
                        .withWithValidationStamp("TESTS")
                        .withWithValidationStampStatus("PASSED"),
                ).single()!!
                assertEquals(validated.id, boundary.build?.id)
                assertEquals(structureService.getValidationRun(passed.id).runTime, boundary.time)
            }
        }
    }

    @Test
    fun `A promotion missing from the branch makes the filter not applicable, and says why`() {
        project {
            branch {
                val vs = findingsStamp()
                scan(vs, "CVE-1")
                scan(vs)

                val boundary = project.boundaries("CVE-1", gold).single()!!
                assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundary.status)
                assertTrue(boundary.reason!!.contains("GOLD"), boundary.reason)
                assertNull(boundary.durationSeconds)
            }
        }
    }

    @Test
    fun `A stamp missing from the branch makes the filter not applicable`() {
        project {
            branch {
                val vs = findingsStamp()
                scan(vs, "CVE-1")
                scan(vs)

                val boundary = project.boundaries(
                    "CVE-1",
                    StandardBuildFilterData.of(10).withWithValidationStamp("NOT-THERE"),
                ).single()!!
                assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundary.status)
                assertTrue(boundary.reason!!.contains("NOT-THERE"), boundary.reason)
            }
        }
    }

    @Test
    fun `A regular expression invalid for the JVM makes the filter not applicable`() {
        project {
            branch {
                val vs = findingsStamp()
                scan(vs, "CVE-1")
                scan(vs)

                val boundary = project.boundaries(
                    "CVE-1",
                    StandardBuildFilterData.of(10).withWithDisplayName("["),
                ).single()!!
                assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundary.status)
                assertNotNull(boundary.reason)
            }
        }
    }

    @Test
    fun `A regular expression only the database refuses makes the filter not applicable`() {
        project {
            branch {
                val vs = findingsStamp()
                scan(vs, "CVE-1")
                scan(vs)
                scan(vs, "CVE-1")
                scan(vs)

                val boundaries = project.boundaries(
                    "CVE-1",
                    StandardBuildFilterData.of(10).withWithDisplayName("""\p{L}+"""),
                )
                assertEquals(2, boundaries.size)
                boundaries.forEach { boundary ->
                    assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundary?.status)
                    assertNotNull(boundary?.reason)
                }
            }
        }
    }

    @Test
    fun `A since criterion makes the filter not applicable`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                scan(vs).build.promote(level)

                val boundary = project.boundaries("CVE-1", gold.withSincePromotionLevel("GOLD")).single()!!
                assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundary.status)
                assertEquals(FindingPeriodBoundaryComputation.SINCE_NOT_USABLE, boundary.reason)
            }
        }
    }

    @Test
    fun `One filter by name applies to each branch, and cannot apply on the one without the level`() {
        project {
            val withGold = branch()
            val withGoldStamp = withGold.findingsStamp(STAMP)
            val level = withGold.promotionLevel("GOLD")
            withGold.scan(withGoldStamp, "CVE-1")
            withGold.scan(withGoldStamp).build.promote(level)
            val withoutGold = branch()
            val withoutGoldStamp = withoutGold.findingsStamp(STAMP)
            withoutGold.scan(withoutGoldStamp, "CVE-1")
            withoutGold.scan(withoutGoldStamp)
            val boundaries = findingQueryService.getFindingExposures(finding("CVE-1"))
                .associate { exposure ->
                    exposure.branch.id() to findingPeriodBoundaryService.getBoundary(
                        findingQueryService.getExposurePeriods(exposure).single(),
                        gold,
                    )?.status
                }
            assertEquals(FindingPeriodBoundaryStatus.MATCHED, boundaries[withGold.id()])
            assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundaries[withoutGold.id()])
        }
    }

    @Test
    fun `A purged fixing build searches from the end of the period, on the creation of the builds`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                val old = Time.now.minusDays(10)
                // Promoted, older than the end of the period
                val older = build { updateBuildSignature(time = old.minusDays(1)) }
                older.promote(level)
                scan(vs, "CVE-1")
                val fixed = scan(vs)
                val promoted = build()
                promoted.promote(level)
                structureService.deleteBuild(fixed.build.id)

                val finding = project.finding("CVE-1")
                val period = findingQueryService.getFindingExposures(finding).flatMap {
                    findingQueryService.getExposurePeriods(it)
                }.single()
                assertNull(period.endedBy)
                assertNotNull(period.period.endedAt)
                val boundary = findingPeriodBoundaryService.getBoundary(period, gold)!!
                assertEquals(FindingPeriodBoundaryStatus.MATCHED, boundary.status)
                assertEquals(promoted.id, boundary.build?.id)
            }
        }
    }

    @Test
    fun `A purged reopening bounds the search by the time it started, on the creation of the builds`() {
        project {
            branch {
                val vs = findingsStamp()
                val level = promotionLevel("GOLD")
                scan(vs, "CVE-1")
                scan(vs)
                val reopened = scan(vs, "CVE-1")
                // The next build, promoted, created after the next period started
                val afterwards = build { updateBuildSignature(time = Time.now.plusDays(1)) }
                afterwards.promote(level)
                structureService.deleteBuild(reopened.build.id)

                val finding = project.finding("CVE-1")
                val (first) = findingQueryService.getFindingExposures(finding).flatMap {
                    findingQueryService.getExposurePeriods(it)
                }
                assertNull(first.nextStartedBy)
                assertNotNull(first.nextPeriod)
                assertEquals(
                    FindingPeriodBoundaryStatus.NOT_REACHED,
                    findingPeriodBoundaryService.getBoundary(first, gold)?.status,
                )
            }
        }
    }

    private fun Project.boundaries(externalId: String, filter: StandardBuildFilterData): List<FindingPeriodBoundary?> =
        findingQueryService.getFindingExposures(finding(externalId)).flatMap { exposure ->
            findingQueryService.getExposurePeriods(exposure).map { period ->
                findingPeriodBoundaryService.getBoundary(period, filter)
            }
        }

    private fun Branch.findingsStamp(name: String = TestUtils.uid("VS")): ValidationStamp =
        validationStamp(
            name = name,
            validationDataTypeConfig = findingsValidationDataType.config(
                CHMLValidationDataTypeConfig(
                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                )
            )
        )

    private fun Branch.scan(vs: ValidationStamp, vararg externalIds: String): FindingsIngestionResult =
        scan(build(), vs, *externalIds)

    private fun scan(build: Build, vs: ValidationStamp, vararg externalIds: String): FindingsIngestionResult =
        findingsIngestionService.ingest(
            build = build,
            request = FindingsIngestionRequest(
                validation = vs.name,
                format = "findings",
                report = """{"scanner": "trivy", "kind": "IMAGE", "findings": [${
                    externalIds.joinToString(",") { entry(it) }
                }]}""".parseAsJson(),
            )
        )

    private val FindingsIngestionResult.build: Build get() = run.build

    private fun entry(externalId: String): String =
        """{"externalId": "$externalId", "location": "pkg:maven/org.x/y", "severity": "HIGH", "title": "Title of $externalId"}"""

    private fun Project.finding(externalId: String): Finding =
        findingRepository.findFindingByKey(id(), "trivy", externalId, "pkg:maven/org.x/y")
            ?: error("Finding $externalId not found")

    private companion object {
        const val STAMP = "SECURITY"
    }
}
