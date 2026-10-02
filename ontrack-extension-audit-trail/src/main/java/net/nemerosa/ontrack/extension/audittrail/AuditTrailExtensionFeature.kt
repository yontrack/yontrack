package net.nemerosa.ontrack.extension.audittrail

import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

@Component
class AuditTrailExtensionFeature(
    environmentsExtensionFeature: EnvironmentsExtensionFeature,
) : AbstractExtensionFeature(
    id = "audit-trail",
    name = "Audit trail",
    description = "Hash-chained trail of every build, endorsed by the instance, and the evidence of its validations",
    options = ExtensionFeatureOptions.DEFAULT
        .withDependency(environmentsExtensionFeature),
)
