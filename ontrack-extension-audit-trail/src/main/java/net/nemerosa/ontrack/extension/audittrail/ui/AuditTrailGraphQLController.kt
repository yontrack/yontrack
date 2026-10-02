package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL root fields of the audit trail.
 */
@Controller
class AuditTrailGraphQLController(
    private val instanceKeyService: InstanceKeyService,
) {

    /**
     * Public keys of the instance, as on `GET /rest/extension/audit-trail/keys`.
     */
    @QueryMapping
    fun auditTrailKeys(): List<InstancePublicKey> = instanceKeyService.getPublicKeys()
}
