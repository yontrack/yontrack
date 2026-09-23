package net.nemerosa.ontrack.extension.findings

import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import org.springframework.stereotype.Component

@Component
class FindingsExtensionFeature : AbstractExtensionFeature(
    id = "findings",
    name = "Security findings",
    description = "Findings reported by security scans: their observations, exposure on branches and acceptance",
)
