package net.nemerosa.ontrack.extension.audittrail.verification

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannel
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The daily verification of the trails: the trails which gained entries since the last run are
 * verified, and each one which fails posts `trail.verification.failed` and is counted.
 *
 * Every test runs the job once first, so that only the trails written by the test are left to
 * verify.
 */
@QueueNoAsync
class TrailVerificationJobIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var trailVerificationJob: TrailVerificationJob

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @Autowired
    private lateinit var mockNotificationChannel: MockNotificationChannel

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    @Test
    fun `A trail tampered with since the last run posts trail verification failed and is counted`() {
        asAdmin {
            project {
                val target = subscribeToFailures()
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        validate(vs)
                        promote(pl)
                        // Tampering with the validation entry, without recomputing its hash
                        namedParameterJdbcTemplate.update(
                            "UPDATE BUILD_TRAIL_ENTRY SET PAYLOAD = :payload WHERE ID = :id",
                            mapOf("id" to entryId(2), "payload" to """{"status":"PASSED","tampered":true}"""),
                        )
                        val failuresBefore = failures()

                        val run = trailVerificationJob.verifyTrails()

                        assertEquals(TrailVerificationJobRun(verifiedTrails = 1, failedTrails = 1), run)
                        assertEquals(failuresBefore + 1, failures())
                        waitForMessages(target, 1)
                        assertEquals(
                            listOf("${project.name}|${branch.name}|$name|2|The hash of the entry is not the hash of its content."),
                            messages(target),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A trail with an intact chain but an invalid endorsement fails at the entry of the endorsement`() {
        asAdmin {
            project {
                val target = subscribeToFailures()
                branch {
                    val vs = validationStamp()
                    val pl = promotionLevel()
                    build {
                        validate(vs)
                        promote(pl)
                        // Tampering with the last entry, its hash recomputed: only its endorsement tells
                        val last = trailService.getEntries(this).last()
                        val tamperedPayload = """{"promotionLevel":{"id":0,"name":"PLATINUM"}}""".parseAsJson()
                        namedParameterJdbcTemplate.update(
                            "UPDATE BUILD_TRAIL_ENTRY SET PAYLOAD = :payload, HASH = :hash WHERE ID = :id",
                            mapOf(
                                "id" to last.id,
                                "payload" to CanonicalJson.canonicalize(tamperedPayload),
                                "hash" to TrailHashFormatV1.hash(last.envelope.copy(payload = tamperedPayload)),
                            ),
                        )

                        assertEquals(TrailVerificationJobRun(verifiedTrails = 1, failedTrails = 1), trailVerificationJob.verifyTrails())
                        waitForMessages(target, 1)
                        val keyId = instanceKeyService.getPublicKeys().single().keyId
                        assertEquals(
                            listOf("${project.name}|${branch.name}|$name|3|The endorsement by key $keyId is not the signature of the hash of the entry."),
                            messages(target),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Intact trails post nothing and are not counted`() {
        asAdmin {
            project {
                val target = subscribeToFailures()
                branch {
                    val vs = validationStamp()
                    build { validate(vs) } // Intact
                    val tampered = build { validate(vs) }
                    namedParameterJdbcTemplate.update(
                        "UPDATE BUILD_TRAIL_ENTRY SET ACTOR = :actor WHERE ID = :id",
                        mapOf("id" to tampered.entryId(1), "actor" to """{"type":"USER","name":"mallory"}"""),
                    )
                    val failuresBefore = failures()

                    val run = trailVerificationJob.verifyTrails()

                    assertEquals(TrailVerificationJobRun(verifiedTrails = 2, failedTrails = 1), run)
                    assertEquals(failuresBefore + 1, failures())
                    // The tampered trail is the sentinel: once its message is there, the intact one would be too
                    waitForMessages(target, 1)
                    assertEquals(
                        listOf("${project.name}|$name|${tampered.name}|1|The hash of the entry is not the hash of its content."),
                        messages(target),
                    )
                }
            }
        }
    }

    @Test
    fun `A trail which gained no entry since the last run is not verified again`() {
        asAdmin {
            project {
                val target = subscribeToFailures()
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs)
                        assertEquals(TrailVerificationJobRun(verifiedTrails = 1, failedTrails = 0), trailVerificationJob.verifyTrails())

                        // Tampered with after its verification, and untouched since
                        namedParameterJdbcTemplate.update(
                            "UPDATE BUILD_TRAIL_ENTRY SET PAYLOAD = :payload WHERE ID = :id",
                            mapOf("id" to entryId(2), "payload" to """{"status":"PASSED","tampered":true}"""),
                        )
                        assertEquals(TrailVerificationJobRun(verifiedTrails = 0, failedTrails = 0), trailVerificationJob.verifyTrails())

                        // A new entry: the whole trail is verified again, the tampered entry with it
                        validate(vs)
                        assertEquals(TrailVerificationJobRun(verifiedTrails = 1, failedTrails = 1), trailVerificationJob.verifyTrails())
                        waitForMessages(target, 1)
                        assertEquals(
                            listOf("${project.name}|${branch.name}|$name|2|The hash of the entry is not the hash of its content."),
                            messages(target),
                            "One message, from the last run only",
                        )
                    }
                }
            }
        }
    }

    /**
     * Subscribes the project to `trail.verification.failed`, after a first run of the job which
     * leaves no trail to verify but those the test writes.
     */
    private fun ProjectEntity.subscribeToFailures(): String {
        trailVerificationJob.verifyTrails()
        val target = uid("t-")
        eventSubscriptionService.subscribe(
            name = uid("s-"),
            channel = mockNotificationChannel,
            channelConfig = MockNotificationChannelConfig(target = target),
            projectEntity = this,
            keywords = null,
            origin = "test",
            contentTemplate = "\${project}|\${branch}|\${build}|\${${AuditTrailEvents.EVENT_FIRST_BROKEN_SEQ}}|\${${AuditTrailEvents.EVENT_REASON}}",
            AuditTrailEvents.TRAIL_VERIFICATION_FAILED,
        )
        return target
    }

    private fun Build.entryId(seq: Int): Int =
        asAdmin { trailService.getEntries(this).single { it.seq == seq }.id }

    private fun failures(): Double =
        meterRegistry.find(AuditTrailMetrics.verificationFailures).counter()?.count() ?: 0.0

    private fun waitForMessages(target: String, count: Int) {
        mockNotificationChannel.waitUntilReceivedCountMessages(
            what = "Waiting for $count message(s) on $target",
            target = target,
            expectedCount = count,
        )
    }

    private fun messages(target: String): List<String> =
        mockNotificationChannel.targetMessages(target).map { it.trim() }
}
