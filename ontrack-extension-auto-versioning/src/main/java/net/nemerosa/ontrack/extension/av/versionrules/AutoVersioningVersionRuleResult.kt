package net.nemerosa.ontrack.extension.av.versionrules

/**
 * Result of the check performed by an [AutoVersioningVersionRule].
 *
 * The rule is the only thing which knows _why_ a version change is not acceptable, and both the
 * audit entry and the notification need that text.
 *
 * @property rejectionReason Reason for the rejection, `null` when the version change is accepted
 */
data class AutoVersioningVersionRuleResult(
    val rejectionReason: String?,
) {

    val accepted: Boolean get() = rejectionReason == null

    companion object {

        /**
         * The version change is accepted.
         */
        fun accepted() = AutoVersioningVersionRuleResult(null)

        /**
         * The version change is rejected, for the given [reason].
         */
        fun rejected(reason: String) = AutoVersioningVersionRuleResult(reason)

    }
}
