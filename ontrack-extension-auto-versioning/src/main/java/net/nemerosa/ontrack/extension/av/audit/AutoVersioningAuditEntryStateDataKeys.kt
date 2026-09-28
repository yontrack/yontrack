package net.nemerosa.ontrack.extension.av.audit

/**
 * List of common keys
 */
object AutoVersioningAuditEntryStateDataKeys {
    /**
     * Name of the branch
     */
    const val BRANCH = "branch"

    /**
     * PR link
     */
    const val PR_LINK = "prLink"

    /**
     * PR name
     */
    const val PR_NAME = "prName"

    /**
     * Commit id
     */
    const val COMMIT_ID = "commitId"

    /**
     * Commit link
     */
    const val COMMIT_LINK = "commitLink"

    /**
     * On the creation of an automatic retry, UUID of the order it retries
     */
    const val RETRY_OF = "retryOf"

    /**
     * On the creation of an automatic retry, UUID of the first order of the chain of retries
     */
    const val RETRY_ORIGINAL = "retryOriginal"

    /**
     * On the creation of an automatic retry, its attempt number (starting at 1)
     */
    const val RETRY_ATTEMPT = "retryAttempt"

    /**
     * On the creation of an automatic retry, the maximum number of automatic retries at the time
     */
    const val RETRY_MAX = "retryMax"

    /**
     * On the error of an order being retried automatically, UUID of its retry
     */
    const val RETRY_UUID = "retryUuid"

    /**
     * On the error of an order being retried automatically, time its retry is scheduled at
     */
    const val RETRY_AT = "retryAt"

    /**
     * On the creation of an order rescheduled manually, UUID of the order it reschedules
     */
    const val RESCHEDULED_FROM = "rescheduledFrom"
}