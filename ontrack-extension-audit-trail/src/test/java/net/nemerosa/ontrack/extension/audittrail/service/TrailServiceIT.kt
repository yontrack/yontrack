package net.nemerosa.ontrack.extension.audittrail.service

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJsonException
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.license.TestLicenseService
import net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEndorsementRepository
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEntryRepository
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrailServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailEntryRepository: TrailEntryRepository

    @Autowired
    private lateinit var trailEndorsementRepository: TrailEndorsementRepository

    @Autowired
    private lateinit var testLicenseService: TestLicenseService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val ci: JsonNode = mapOf("account" to "ci-bot", "via" to "token", "tokenName" to "ci-demo").asJson()

    @Test
    fun `A trail opened by the creation of its build starts with build created at seq 1, and chains the next entries`() {
        asAdmin {
            project {
                branch {
                    build {
                        val created = trailService.append(this, TrailEntryTypes.BUILD_CREATED, buildPayload(), ci)
                        val promoted = trailService.append(
                            this,
                            "promotion.added",
                            mapOf("promotionLevel" to "GOLD", "runId" to 12).asJson(),
                            ci
                        )

                        val entries = trailService.getEntries(this)
                        assertEquals(listOf(created, promoted), entries)
                        val (first, second) = entries
                        assertEquals(1, first.seq)
                        assertEquals(TrailEntryTypes.BUILD_CREATED, first.type)
                        assertNull(first.prevHash)
                        assertEquals(2, second.seq)
                        assertEquals("promotion.added", second.type)
                        assertEquals(first.hash, second.prevHash)
                        assertEquals(mapOf("promotionLevel" to "GOLD", "runId" to 12).asJson(), second.payload)
                        assertEquals(ci, second.actor)
                        entries.forEach { it.assertWellFormed(this) }
                    }
                }
            }
        }
    }

    @Test
    fun `A build which predates its trail gets a partial trail, opened by trail opened at seq 1`() {
        asAdmin {
            project {
                branch {
                    build {
                        val validated = trailService.append(
                            this,
                            "validation.run",
                            mapOf("validationStamp" to "unit-tests", "status" to "PASSED").asJson(),
                            ci
                        )
                        assertNotNull(validated)

                        val entries = trailService.getEntries(this)
                        assertEquals(2, entries.size)
                        val (opened, entry) = entries
                        assertEquals(1, opened.seq)
                        assertEquals(TrailEntryTypes.TRAIL_OPENED, opened.type)
                        assertNull(opened.prevHash)
                        assertEquals(
                            mapOf(
                                "build" to mapOf(
                                    "id" to id(),
                                    "project" to project.name,
                                    "branch" to branch.name,
                                    "name" to name,
                                ),
                                "buildCreatedAt" to signature.time.toHashedTime(),
                                "partial" to true,
                            ).asJson(),
                            opened.payload
                        )
                        assertEquals(ci, opened.actor)
                        assertEquals(entry.time, opened.time)
                        assertEquals(validated, entry)
                        assertEquals(2, entry.seq)
                        assertEquals(opened.hash, entry.prevHash)
                        entries.forEach { it.assertWellFormed(this) }

                        // Opened once only
                        trailService.append(this, "validation.run", mapOf("status" to "FAILED").asJson(), ci)
                        assertEquals(
                            listOf(TrailEntryTypes.TRAIL_OPENED, "validation.run", "validation.run"),
                            trailService.getEntries(this).map { it.type }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Nothing is written while the licence is off, and the trail opens partial when it comes back`() {
        asAdmin {
            project {
                branch {
                    build {
                        testLicenseService.withoutFeature(FEATURE_AUDIT_TRAIL) {
                            assertNull(trailService.append(this, TrailEntryTypes.BUILD_CREATED, buildPayload(), ci))
                            assertNull(trailService.append(this, "validation.run", mapOf("status" to "PASSED").asJson(), ci))
                        }
                        assertTrue(trailService.getEntries(this).isEmpty(), "Nothing written without the licence")

                        trailService.append(this, "promotion.added", mapOf("promotionLevel" to "GOLD").asJson(), ci)
                        assertEquals(
                            listOf(TrailEntryTypes.TRAIL_OPENED, "promotion.added"),
                            trailService.getEntries(this).map { it.type }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `The trail of a build is deleted with the build`() {
        asAdmin {
            project {
                branch {
                    val build = build {
                        trailService.append(this, TrailEntryTypes.BUILD_CREATED, buildPayload(), ci)
                        trailService.append(this, "promotion.added", mapOf("promotionLevel" to "GOLD").asJson(), ci)
                    }
                    val other = build {
                        trailService.append(this, TrailEntryTypes.BUILD_CREATED, buildPayload(), ci)
                    }
                    assertEquals(2, trailEntryRepository.findEntries(build.id()).size)
                    assertEquals(2, trailEndorsementRepository.findEndorsements(build.id()).size)

                    structureService.deleteBuild(build.id)

                    assertTrue(trailEntryRepository.findEntries(build.id()).isEmpty(), "Trail deleted with its build")
                    assertTrue(
                        trailEndorsementRepository.findEndorsements(build.id()).isEmpty(),
                        "Endorsements deleted with their entries"
                    )
                    assertEquals(1, trailService.getEntries(other).size, "Trail of the other build untouched")
                    assertEquals(1, trailService.getEndorsements(other).size, "Endorsements of the other build untouched")
                }
            }
        }
    }

    @Test
    fun `A payload outside the canonical subset is rejected, and nothing is written`() {
        asAdmin {
            project {
                branch {
                    build {
                        trailService.append(this, TrailEntryTypes.BUILD_CREATED, buildPayload(), ci)
                        assertThrows<CanonicalJsonException> {
                            trailService.append(this, "validation.data", mapOf("coverage" to 87.5).asJson(), ci)
                        }
                        assertEquals(listOf(TrailEntryTypes.BUILD_CREATED), trailService.getEntries(this).map { it.type })
                    }
                }
            }
        }
    }

    @Test
    fun `Every append is timed`() {
        asAdmin {
            project {
                branch {
                    build {
                        val count = { type: String ->
                            meterRegistry.find(AuditTrailMetrics.append)
                                .tag(AuditTrailMetrics.Tags.TYPE, type)
                                .timer()?.count() ?: 0L
                        }
                        val createdBefore = count(TrailEntryTypes.BUILD_CREATED)
                        val promotedBefore = count("promotion.added")
                        trailService.append(this, TrailEntryTypes.BUILD_CREATED, buildPayload(), ci)
                        trailService.append(this, "promotion.added", mapOf("promotionLevel" to "GOLD").asJson(), ci)
                        assertEquals(createdBefore + 1, count(TrailEntryTypes.BUILD_CREATED))
                        assertEquals(promotedBefore + 1, count("promotion.added"))
                    }
                }
            }
        }
    }

    private fun Build.buildPayload(): JsonNode = mapOf(
        "build" to mapOf(
            "id" to id(),
            "project" to project.name,
            "branch" to branch.name,
            "name" to name,
        )
    ).asJson()

    /**
     * The time as hashed, worked independently of the hash format: UTC, three digits of milliseconds.
     */
    private fun LocalDateTime.toHashedTime(): String =
        String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03dZ", year, monthValue, dayOfMonth, hour, minute, second, nano / 1_000_000)

    private fun TrailEntry.assertWellFormed(build: Build) {
        assertEquals(build.id(), buildId)
        assertEquals(1, schemaVersion)
        assertTrue(Regex("[0-9a-f]{64}").matches(hash), "Hash is lowercase hex SHA-256")
        assertEquals(0, time.nano % 1_000_000, "Time at the millisecond")
        assertEquals(hash, TrailHashFormatV1.hash(envelope))
    }
}
