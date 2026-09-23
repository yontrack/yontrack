package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState

/**
 * Filter on the findings of a project. Every criterion is optional, and they all apply together.
 */
@APIDescription("Filter on the findings of a project. Every criterion is optional, and they all apply together.")
data class FindingFilter(
    @APIDescription("Maximum severity of the finding across its observations")
    val severity: FindingSeverity? = null,
    @APIDescription("State of the finding: in the project, or on the branch when one is given")
    val state: FindingState? = null,
    @APIDescription("Name of a branch the finding has been exposed on, resolved or not. The state then applies to this branch.")
    val branch: String? = null,
    @APIDescription("Name of the scanner which reported the finding")
    val scanner: String? = null,
    @APIDescription("Kind of scan which reported the finding")
    val kind: FindingKind? = null,
)
