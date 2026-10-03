package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.tampering.DemoTamperingCondition
import net.nemerosa.ontrack.extension.audittrail.tampering.DemoTamperingService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * Tampering with a trail, for demonstration only — registered only while
 * `ontrack.extension.audit-trail.demo-tampering.enabled` is `true`, so that the endpoint answers
 * 404 otherwise, as if it had never existed.
 */
@RestController
@RequestMapping("/rest/extension/audit-trail/demo-tampering")
@DemoTamperingCondition
class DemoTamperingController(
    private val demoTamperingService: DemoTamperingService,
) {

    /**
     * Replaces the payload of an entry, recomputing nothing, so that the verification of the trail
     * breaks at it. Global administrators only.
     *
     * @param buildId ID of the build
     * @param seq Position of the entry in the trail of the build
     * @param payload New payload, a JSON object
     * @return Entry, with its new payload and its unchanged hash
     */
    @PutMapping("builds/{buildId}/entries/{seq}/payload")
    fun rewritePayload(
        @PathVariable buildId: Int,
        @PathVariable seq: Int,
        @RequestBody payload: JsonNode,
    ): AuditTrailEntryView =
        AuditTrailEntryView.of(demoTamperingService.rewritePayload(buildId, seq, payload))
}
