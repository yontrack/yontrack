package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public keys of the instance, with which the endorsements of its trails are verified.
 *
 * Public by nature: any authenticated user reads them, whether the licence is on or not — a trail
 * stays verifiable after the licence lapses.
 */
@RestController
@RequestMapping("/rest/extension/audit-trail")
class AuditTrailKeysController(
    private val instanceKeyService: InstanceKeyService,
) {

    /**
     * Public keys of the instance: `[{keyId, algorithm, publicKey}]`, the public key in PEM. Empty
     * while no key exists.
     */
    @GetMapping("keys")
    fun getKeys(): List<InstancePublicKey> = instanceKeyService.getPublicKeys()
}
