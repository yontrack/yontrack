package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundary
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundaryStatus
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData
import net.nemerosa.ontrack.model.structure.ValidationRun
import java.time.Duration
import java.time.LocalDateTime

/**
 * The rules of a [FindingPeriodBoundary], without any access to the data.
 */
object FindingPeriodBoundaryComputation {

    /**
     * Why a filter with a `since*` criterion cannot apply.
     */
    const val SINCE_NOT_USABLE: String =
        "A \"since\" criterion cannot set a boundary: its result moves with the head of the branch."

    /**
     * Why a filter cannot apply because of its `since*` criteria, `null` when it has none.
     */
    fun sinceCriterionReason(filter: StandardBuildFilterData): String? =
        SINCE_NOT_USABLE.takeIf {
            listOf(
                filter.sincePromotionLevel,
                filter.sinceValidationStamp,
                filter.sinceValidationStampStatus,
                filter.sinceProperty,
                filter.sincePropertyValue,
            ).any { !it.isNullOrBlank() }
        }

    /**
     * The outcome for a period.
     *
     * @param hasNextPeriod Whether the same exposure has a later period
     * @param notApplicable Why the filter cannot apply on this branch, if so
     * @param match The first matching build, with the time it started to match
     * @return The boundary, `null` while the period is open
     */
    fun boundary(
        period: FindingExposurePeriod,
        hasNextPeriod: Boolean,
        notApplicable: String?,
        match: Pair<Build, LocalDateTime>?,
        now: LocalDateTime,
    ): FindingPeriodBoundary? {
        val endedAt = period.endedAt ?: return null
        return when {
            notApplicable != null -> FindingPeriodBoundary(
                status = FindingPeriodBoundaryStatus.NOT_APPLICABLE,
                reason = notApplicable,
            )

            match != null -> {
                // A build promoted before its scan was posted has its boundary at the time of the scan
                val time = match.second.coerceAtLeast(endedAt)
                FindingPeriodBoundary(
                    status = FindingPeriodBoundaryStatus.MATCHED,
                    build = match.first,
                    time = time,
                    durationSeconds = seconds(period.startedAt, time),
                )
            }

            hasNextPeriod -> FindingPeriodBoundary(status = FindingPeriodBoundaryStatus.NOT_REACHED)

            else -> FindingPeriodBoundary(
                status = FindingPeriodBoundaryStatus.NONE_YET,
                durationSeconds = seconds(period.startedAt, now),
            )
        }
    }

    /**
     * When a build started to match a filter: its promotion for a promotion criterion, its validation
     * for a stamp criterion, its creation for any other criterion or for none, and the latest of them
     * when there are several.
     *
     * @param created Creation of the build
     * @param promoted First promotion run of the level of the filter on the build, if any
     * @param validated See [validatedSince]
     */
    fun matchingSince(
        filter: StandardBuildFilterData,
        created: LocalDateTime,
        promoted: LocalDateTime?,
        validated: LocalDateTime?,
    ): LocalDateTime {
        val byPromotion = promoted.takeIf { !filter.withPromotionLevel.isNullOrBlank() }
        val byValidation = validated.takeIf { !filter.withValidationStamp.isNullOrBlank() }
        val byOther = listOf(
            filter.withProperty,
            filter.linkedFrom,
            filter.linkedTo,
            filter.withDisplayName,
            filter.assisted,
            filter.actor,
            filter.afterDate,
            filter.beforeDate,
        ).any { it != null && it.toString().isNotBlank() }
        return listOfNotNull(
            byPromotion,
            byValidation,
            created.takeIf { byOther || (byPromotion == null && byValidation == null) },
        ).max()
    }

    /**
     * When a build was validated: the first run of the stamp on the build, or, with a status, the first
     * time one of its runs had this status.
     *
     * @param runs Runs of the stamp on the build
     * @param status Status of the filter, if any
     */
    fun validatedSince(runs: List<ValidationRun>, status: String?): LocalDateTime? =
        runs.flatMap { it.validationRunStatuses }
            .filter { status.isNullOrBlank() || it.statusID.id == status }
            .minOfOrNull { it.signature.time }

    private fun seconds(from: LocalDateTime, to: LocalDateTime): Long =
        Duration.between(from, to).seconds.coerceAtLeast(0)
}
