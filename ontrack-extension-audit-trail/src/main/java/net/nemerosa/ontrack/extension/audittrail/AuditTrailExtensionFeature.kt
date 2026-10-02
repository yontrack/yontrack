package net.nemerosa.ontrack.extension.audittrail

import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import org.springframework.stereotype.Component

@Component
class AuditTrailExtensionFeature : AbstractExtensionFeature(
    id = "audit-trail",
    name = "Audit trail",
    description = "Hash-chained trail of every build, endorsed by the instance, and the evidence of its validations",
)
