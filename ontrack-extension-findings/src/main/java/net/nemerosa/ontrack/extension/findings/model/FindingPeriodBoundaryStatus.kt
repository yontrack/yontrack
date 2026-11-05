package net.nemerosa.ontrack.extension.findings.model

/**
 * How a period of an exposure which ended reads against a build filter.
 */
enum class FindingPeriodBoundaryStatus {
    /** A build matches the filter, at or after the end of the period and before the next one. */
    MATCHED,

    /** No build matches the filter yet, and the finding has not come back: still waiting on one. */
    NONE_YET,

    /** The finding came back before any build matched the filter: it needs a new fix. */
    NOT_REACHED,

    /** The filter cannot apply to this branch, which is not waiting for any build. */
    NOT_APPLICABLE,
}
