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
) {
    /**
     * Whether the acceptance still holds on the given day: it has no expiry, or its expiry is
     * not past. An expiry is evaluated when it is read, never by a job.
     */
    fun isEffectiveOn(date: LocalDate): Boolean = expiresAt == null || !expiresAt.isBefore(date)
}
