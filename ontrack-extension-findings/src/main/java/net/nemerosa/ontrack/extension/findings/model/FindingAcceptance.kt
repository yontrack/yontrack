package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDate

/**
 * A decision recorded outside Yontrack, read by it, that a finding is tolerated, possibly until
 * an expiry.
 *
 * @property statement Why the finding is tolerated
 * @property expiresAt Last day the acceptance holds, if any
 * @property source Where the decision is recorded (a suppressions file…)
 */
data class FindingAcceptance(
    val statement: String?,
    val expiresAt: LocalDate?,
    val source: String?,
)
