package net.nemerosa.ontrack.extension.audittrail.service

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEntryRepository
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.metrics.time
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional
class TrailServiceImpl(
    private val trailEntryRepository: TrailEntryRepository,
    private val auditTrailLicense: AuditTrailLicense,
    private val securityService: SecurityService,
    private val meterRegistry: MeterRegistry,
) : TrailService {

    override fun append(build: Build, type: String, payload: JsonNode, actor: JsonNode): TrailEntry? {
        require(type.isNotBlank()) { "The type of a trail entry must not be blank." }
        require(payload.isObject) { "The payload of a trail entry must be a JSON object." }
        require(actor.isObject) { "The actor of a trail entry must be a JSON object." }
        if (!auditTrailLicense.auditTrailEnabled) {
            return null
        }
        return meterRegistry.time(AuditTrailMetrics.append, AuditTrailMetrics.Tags.TYPE to type) {
            val buildId = build.id()
            trailEntryRepository.lockTrail(buildId)
            val time = Time.now.truncatedTo(ChronoUnit.MILLIS)
            var last = trailEntryRepository.findLastEntry(buildId)
            if (last == null && type != TrailEntryTypes.BUILD_CREATED) {
                last = write(build, null, TrailEntryTypes.TRAIL_OPENED, openedPayload(build), actor, time)
            }
            write(build, last, type, payload, actor, time)
        }
    }

    override fun getEntries(build: Build): List<TrailEntry> {
        securityService.checkProjectFunction(build, ProjectView::class.java)
        return trailEntryRepository.findEntries(build.id())
    }

    private fun write(
        build: Build,
        last: TrailEntry?,
        type: String,
        payload: JsonNode,
        actor: JsonNode,
        time: LocalDateTime,
    ): TrailEntry {
        // The entry carries what is stored and hashed: the canonical forms, read back
        val canonicalPayload = CanonicalJson.canonicalize(payload)
        val canonicalActor = CanonicalJson.canonicalize(actor)
        val entry = TrailEntry(
            id = 0,
            buildId = build.id(),
            seq = (last?.seq ?: 0) + 1,
            schemaVersion = TrailHashFormatV1.SCHEMA_VERSION,
            type = type,
            payload = canonicalPayload.parseAsJson(),
            actor = canonicalActor.parseAsJson(),
            time = time,
            prevHash = last?.hash,
            hash = "",
        )
        val envelope = entry.envelope
        return trailEntryRepository.insert(
            entry = entry.copy(hash = TrailHashFormatV1.hash(envelope)),
            canonicalPayload = canonicalPayload,
            canonicalActor = canonicalActor,
            time = envelope.time,
        )
    }

    /**
     * Payload of the `trail.opened` entry: the build, its creation time, and the trail marked as
     * partial.
     */
    private fun openedPayload(build: Build): JsonNode = mapOf(
        "build" to mapOf(
            "id" to build.id(),
            "project" to build.project.name,
            "branch" to build.branch.name,
            "name" to build.name,
        ),
        "buildCreatedAt" to TrailHashFormatV1.formatTime(build.signature.time),
        "partial" to true,
    ).asJson()
}
