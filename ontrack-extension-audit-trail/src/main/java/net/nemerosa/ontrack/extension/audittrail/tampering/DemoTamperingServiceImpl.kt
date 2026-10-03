package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJsonException
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEntryRepository
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode

@Service
@Transactional
@DemoTamperingCondition
class DemoTamperingServiceImpl(
    private val trailEntryRepository: TrailEntryRepository,
    private val securityService: SecurityService,
    private val structureService: StructureService,
) : DemoTamperingService {

    private val logger = LoggerFactory.getLogger(DemoTamperingServiceImpl::class.java)

    override fun rewritePayload(buildId: Int, seq: Int, payload: JsonNode): TrailEntry {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        if (!payload.isObject) {
            throw DemoTamperingPayloadException("The payload of a trail entry must be a JSON object.")
        }
        // Stored as the canonical text, like any payload, so that the entry stays readable
        val canonicalPayload = try {
            CanonicalJson.canonicalize(payload)
        } catch (ex: CanonicalJsonException) {
            throw DemoTamperingPayloadException(ex.message ?: "Invalid payload.")
        }
        // The build must exist
        structureService.getBuild(ID.of(buildId))
        // Not interleaved with an append to the same trail
        trailEntryRepository.lockTrail(buildId)
        val entry = trailEntryRepository.findEntry(buildId, seq)
            ?: throw TrailEntryNotFoundException(buildId, seq)
        trailEntryRepository.tamperPayload(entry.id, canonicalPayload)
        logger.warn(
            "[audit-trail] Demo tampering: payload of the entry {} of the trail of the build {} rewritten by {}.",
            seq,
            buildId,
            securityService.currentUser?.account?.email,
        )
        return entry.copy(payload = canonicalPayload.parseAsJson())
    }
}
