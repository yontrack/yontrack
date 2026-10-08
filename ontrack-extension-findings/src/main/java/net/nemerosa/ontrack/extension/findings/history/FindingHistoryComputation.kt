package net.nemerosa.ontrack.extension.findings.history

import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingObservationSighting
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Building the history of a finding from the periods of its exposures and its observations.
 *
 * Nothing of it is stored but the periods: the acceptance entries are derived from the
 * observations, which carry the acceptance of their scan, and an expiry is evaluated when read.
 */
object FindingHistoryComputation {

    /**
     * History of a finding, the most recent first.
     *
     * Each period gives an entry for its start — the [discovery][FindingHistoryEntryType.DISCOVERED]
     * for the earliest period of the finding, an [exposure][FindingHistoryEntryType.EXPOSED] for the
     * first period of any other branch and stamp, a [reopening][FindingHistoryEntryType.REOPENED]
     * otherwise — and one for its end, if any. The changes of acceptance within a period are
     * entries, and the observations between two entries are [grouped][FindingHistoryEntryType.OBSERVATIONS].
     * The observation which started a period, or an acceptance, is the entry itself.
     *
     * Observations outside any period, made before the periods were kept, are grouped on their own.
     *
     * @param periods Periods of the exposures of the finding
     * @param sightings Observations of the finding, with their branch and stamp
     * @param today Day against which the expiry of the acceptances is evaluated
     */
    fun history(
        periods: List<FindingExposurePeriod>,
        sightings: List<FindingObservationSighting>,
        today: LocalDate,
    ): List<FindingHistoryEntry> {
        val discovery = periods.minWithOrNull(periodOrder)
        val periodsByKey = periods.groupBy { Key(it.branchId, it.validationStampId) }
        val sightingsByKey = sightings.groupBy { Key(it.branchId, it.validationStampId) }
        val keys = (periodsByKey.keys + sightingsByKey.keys)
            .sortedWith(compareBy<Key> { it.branchId }.thenBy { it.validationStampId })

        val entries = mutableListOf<FindingHistoryEntry>()
        keys.forEach { key ->
            val keyPeriods = periodsByKey[key].orEmpty().sortedWith(periodOrder)
            val keySightings = sightingsByKey[key].orEmpty().sortedWith(sightingOrder)
            val group = mutableListOf<FindingObservationSighting>()
            fun flush() {
                if (group.isNotEmpty()) {
                    entries += observations(key, group)
                    group.clear()
                }
            }

            var index = 0
            keyPeriods.forEachIndexed { periodIndex, period ->
                // Observations before the period, outside any
                while (index < keySightings.size && keySightings[index].observation.time < period.startedAt) {
                    group += keySightings[index++]
                }
                flush()
                // Start of the period
                entries += FindingHistoryEntry(
                    type = when {
                        period == discovery -> FindingHistoryEntryType.DISCOVERED
                        periodIndex == 0 -> FindingHistoryEntryType.EXPOSED
                        else -> FindingHistoryEntryType.REOPENED
                    },
                    time = period.startedAt,
                    branchId = key.branchId,
                    validationStampId = key.validationStampId,
                    validationRunId = period.startedByValidationRunId,
                    build = period.startedInBuild,
                )
                // Within the period
                val within = mutableListOf<FindingObservationSighting>()
                while (index < keySightings.size && period.endedAt.let { it == null || keySightings[index].observation.time < it }) {
                    within += keySightings[index++]
                }
                walk(period, within, today).forEach { step ->
                    when (step) {
                        is Step.Observed -> if (!step.opening) group += step.sighting
                        is Step.Accepted -> {
                            flush()
                            entries += FindingHistoryEntry(
                                type = FindingHistoryEntryType.ACCEPTED,
                                time = step.sighting.observation.time,
                                branchId = key.branchId,
                                validationStampId = key.validationStampId,
                                validationRunId = step.sighting.observation.validationRunId,
                                acceptance = step.acceptance,
                            )
                        }

                        is Step.Withdrawn -> {
                            flush()
                            entries += FindingHistoryEntry(
                                type = FindingHistoryEntryType.ACCEPTANCE_WITHDRAWN,
                                time = step.sighting.observation.time,
                                branchId = key.branchId,
                                validationStampId = key.validationStampId,
                                validationRunId = step.sighting.observation.validationRunId,
                            )
                        }

                        is Step.Expired -> {
                            flush()
                            entries += FindingHistoryEntry(
                                type = FindingHistoryEntryType.ACCEPTANCE_EXPIRED,
                                time = step.time,
                                branchId = key.branchId,
                                validationStampId = key.validationStampId,
                                acceptance = step.acceptance,
                            )
                        }
                    }
                }
                flush()
                // End of the period
                if (period.endedAt != null) {
                    entries += FindingHistoryEntry(
                        type = FindingHistoryEntryType.RESOLVED,
                        time = period.endedAt,
                        branchId = key.branchId,
                        validationStampId = key.validationStampId,
                        validationRunId = period.endedByValidationRunId,
                        build = period.endedInBuild,
                        resolutionReason = period.resolutionReason,
                    )
                }
            }
            // Observations after the last period, outside any
            while (index < keySightings.size) {
                group += keySightings[index++]
            }
            flush()
        }

        // The most recent first, the entries at the same time in the reverse order of their building
        return entries.withIndex()
            .sortedWith(compareByDescending<IndexedValue<FindingHistoryEntry>> { it.value.time }.thenByDescending { it.index })
            .map { it.value }
    }

    /**
     * Stretches of a period during which the finding was reported under an acceptance holding on
     * the day of each observation, the oldest first.
     *
     * @param period Period
     * @param sightings Observations of the finding — those outside the period, or of another branch
     * or stamp, are ignored
     * @param today Day against which the expiry of the acceptances is evaluated
     */
    fun acceptedSpans(
        period: FindingExposurePeriod,
        sightings: List<FindingObservationSighting>,
        today: LocalDate,
    ): List<FindingAcceptedSpan> {
        val within = sightings
            .filter {
                it.branchId == period.branchId &&
                        it.validationStampId == period.validationStampId &&
                        it.observation.time >= period.startedAt &&
                        (period.endedAt == null || it.observation.time < period.endedAt)
            }
            .sortedWith(sightingOrder)
        val spans = mutableListOf<FindingAcceptedSpan>()
        var open: FindingAcceptedSpan? = null
        walk(period, within, today).forEach { step ->
            when (step) {
                is Step.Accepted -> open = FindingAcceptedSpan(
                    from = step.sighting.observation.time,
                    to = null,
                    acceptance = step.acceptance,
                    fromValidationRunId = step.sighting.observation.validationRunId,
                )

                is Step.Withdrawn -> {
                    open?.let { spans += it.copy(to = step.sighting.observation.time) }
                    open = null
                }

                is Step.Expired -> {
                    open?.let { spans += it.copy(to = step.time) }
                    open = null
                }

                is Step.Observed -> {}
            }
        }
        open?.let { spans += it.copy(to = period.endedAt) }
        return spans
    }

    /**
     * Going through the observations of a period, oldest first, with the changes of acceptance
     * between them.
     */
    private fun walk(
        period: FindingExposurePeriod,
        within: List<FindingObservationSighting>,
        today: LocalDate,
    ): List<Step> {
        val steps = mutableListOf<Step>()
        var accepted: FindingAcceptance? = null
        within.forEachIndexed { index, sighting ->
            val observation = sighting.observation
            // An acceptance which expired before this observation
            accepted?.expiry?.takeIf { it <= observation.time }?.let { expiry ->
                steps += Step.Expired(expiry, accepted!!)
                accepted = null
            }
            val acceptance = observation.acceptance?.takeIf { it.isEffectiveOn(observation.time.toLocalDate()) }
            val opening = index == 0 && (
                    observation.time == period.startedAt ||
                            observation.validationRunId == period.startedByValidationRunId
                    )
            when {
                acceptance != null && accepted == null -> steps += Step.Accepted(sighting, acceptance)
                acceptance == null && accepted != null -> steps += Step.Withdrawn(sighting)
                else -> steps += Step.Observed(sighting, opening)
            }
            accepted = acceptance
        }
        // An acceptance which expired within the period, by the end of the period or by today
        accepted?.expiry?.let { expiry ->
            val expired = if (period.endedAt != null) {
                expiry < period.endedAt
            } else {
                expiry <= today.atStartOfDay()
            }
            if (expired) {
                steps += Step.Expired(expiry, accepted!!)
            }
        }
        return steps
    }

    /**
     * Group of observations
     */
    private fun observations(key: Key, group: List<FindingObservationSighting>): FindingHistoryEntry {
        val first = group.first().observation
        val last = group.last().observation
        return FindingHistoryEntry(
            type = FindingHistoryEntryType.OBSERVATIONS,
            time = last.time,
            branchId = key.branchId,
            validationStampId = key.validationStampId,
            count = group.size,
            firstTime = first.time,
            lastTime = last.time,
            firstValidationRunId = first.validationRunId,
            lastValidationRunId = last.validationRunId,
        )
    }

    /**
     * Time an acceptance stops holding: the day after its last one, at midnight
     */
    private val FindingAcceptance.expiry: LocalDateTime?
        get() = expiresAt?.plusDays(1)?.atStartOfDay()

    private data class Key(val branchId: Int, val validationStampId: Int)

    private sealed interface Step {
        data class Observed(val sighting: FindingObservationSighting, val opening: Boolean) : Step
        data class Accepted(val sighting: FindingObservationSighting, val acceptance: FindingAcceptance) : Step
        data class Withdrawn(val sighting: FindingObservationSighting) : Step
        data class Expired(val time: LocalDateTime, val acceptance: FindingAcceptance) : Step
    }

    /**
     * The oldest period first
     */
    val periodOrder: Comparator<FindingExposurePeriod> = compareBy<FindingExposurePeriod> { it.startedAt }.thenBy { it.id }

    private val sightingOrder = compareBy<FindingObservationSighting> { it.observation.time }
        .thenBy { it.observation.validationRunId }
}
