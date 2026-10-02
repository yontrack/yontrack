package net.nemerosa.ontrack.extension.audittrail.export

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TrailExportServiceImpl(
    private val trailService: TrailService,
    private val instanceKeyService: InstanceKeyService,
) : TrailExportService {

    override fun export(build: Build): TrailExport {
        val entries = trailService.getEntries(build)
        val endorsements = trailService.getEndorsements(build).groupBy { it.entryId }
        return TrailExport(
            exportVersion = TrailExport.EXPORT_VERSION,
            exportedAt = TrailHashFormatV1.formatTime(Time.now),
            build = TrailExportBuild(
                id = build.id(),
                project = build.project.name,
                branch = build.branch.name,
                name = build.name,
            ),
            keys = instanceKeyService.getPublicKeys(),
            entries = entries.map { entry ->
                val envelope = entry.envelope
                TrailExportEntry(
                    seq = envelope.seq,
                    schemaVersion = envelope.schemaVersion,
                    type = envelope.type,
                    time = envelope.time,
                    actor = envelope.actor,
                    prevHash = envelope.prevHash,
                    payload = envelope.payload,
                    hash = entry.hash,
                    endorsements = endorsements[entry.id].orEmpty().map { endorsement ->
                        TrailExportEndorsement(
                            keyId = endorsement.keyId,
                            signature = endorsement.signature,
                            time = TrailHashFormatV1.formatTime(endorsement.time),
                        )
                    },
                )
            },
        )
    }
}
