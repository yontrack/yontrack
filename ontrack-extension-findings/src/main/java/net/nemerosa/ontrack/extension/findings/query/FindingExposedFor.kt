package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingExposureEpisode
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import java.time.LocalDate

/**
 * How long a finding has been exposed on a set of branches, as the project findings table shows it.
 *
 * @property ongoing The longest ongoing period, on one branch for one stamp: the one which started
 * first. `null` when none is ongoing.
 * @property accepted Whether the exposure of this period is accepted
 * @property reopened Whether this period follows an earlier one of the same exposure
 * @property lastEpisode The latest [episode][FindingExposureEpisode] of the periods, `null` when
 * there is none. For a fixed finding, its length is how long the last fix took.
 */
data class FindingExposedFor(
    val ongoing: FindingExposurePeriod?,
    val accepted: Boolean,
    val reopened: Boolean,
    val lastEpisode: FindingExposureEpisode?,
) {
    companion object {

        /**
         * Nothing to show: no period
         */
        val NONE = FindingExposedFor(ongoing = null, accepted = false, reopened = false, lastEpisode = null)

        /**
         * How long a finding has been exposed.
         *
         * @param periods Periods of the finding, on the branches to consider
         * @param exposures Exposures of the finding, which give the acceptance of the ongoing period
         * @param date Day against which the expiry of the acceptances is evaluated
         */
        fun of(
            periods: Collection<FindingExposurePeriod>,
            exposures: Collection<FindingExposure>,
            date: LocalDate,
        ): FindingExposedFor {
            if (periods.isEmpty()) return NONE
            val ongoing = periods
                .filter { it.ongoing }
                .minWithOrNull(compareBy<FindingExposurePeriod> { it.startedAt }.thenBy { it.id })
            val exposure = ongoing?.let { period ->
                exposures.find { it.branchId == period.branchId && it.validationStampId == period.validationStampId }
            }
            return FindingExposedFor(
                ongoing = ongoing,
                accepted = exposure?.stateOn(date) == FindingExposureState.ACCEPTED,
                reopened = ongoing != null && periods.any {
                    it.branchId == ongoing.branchId &&
                            it.validationStampId == ongoing.validationStampId &&
                            it.startedAt < ongoing.startedAt
                },
                lastEpisode = FindingExposureEpisode.of(periods).lastOrNull(),
            )
        }
    }
}
