package net.nemerosa.ontrack.extension.audittrail.tampering

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import tools.jackson.databind.JsonNode

/**
 * Tampering with the trails, for demonstration only, so that a demonstration can show a broken
 * chain through the API alone.
 *
 * Only exists while `ontrack.extension.audit-trail.demo-tampering.enabled` is `true`
 * ([DemoTamperingCondition]); there is no such service otherwise.
 */
interface DemoTamperingService {

    /**
     * Replaces the payload of an entry of the trail of a build, recomputing nothing: the hash, the
     * previous hash and the endorsements of the entry stay as they are, so that the verification of
     * the trail breaks at this entry.
     *
     * Global administrators only (`ApplicationManagement`), checked before anything else.
     *
     * @param buildId ID of the build whose trail is tampered with
     * @param seq Position of the entry in the trail
     * @param payload New payload: a JSON object within the canonical JSON a trail accepts, so that
     * the entry stays readable
     * @return Entry, with its new payload
     * @throws DemoTamperingPayloadException When the new payload is not a JSON object a trail accepts
     * @throws TrailEntryNotFoundException When the trail has no entry at this position
     */
    fun rewritePayload(buildId: Int, seq: Int, payload: JsonNode): TrailEntry
}
