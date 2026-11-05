package net.nemerosa.ontrack.extension.findings.model

import net.nemerosa.ontrack.model.structure.Build
import java.time.LocalDateTime

/**
 * The first build at or after the end of a period of an exposure, and before the next period starts,
 * which matches a build filter chosen by the reader.
 *
 * It is a way of reading a period and is never stored: it follows later promotions and any change of
 * filter, and the state of the finding, its periods and its history stay on the raw scan facts.
 *
 * @property status Outcome for this period
 * @property build Matching build, for [FindingPeriodBoundaryStatus.MATCHED] only
 * @property time When the build started to match, for [FindingPeriodBoundaryStatus.MATCHED] only
 * @property durationSeconds From the start of the period to the boundary, or to now for
 * [FindingPeriodBoundaryStatus.NONE_YET], `null` otherwise
 * @property reason Why the filter cannot apply, for [FindingPeriodBoundaryStatus.NOT_APPLICABLE] only
 */
data class FindingPeriodBoundary(
    val status: FindingPeriodBoundaryStatus,
    val build: Build? = null,
    val time: LocalDateTime? = null,
    val durationSeconds: Long? = null,
    val reason: String? = null,
)
