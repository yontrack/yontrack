package net.nemerosa.ontrack.extension.findings.model

import java.time.Duration
import java.time.LocalDateTime

/**
 * An exposure episode: the union of the [periods][FindingExposurePeriod] of a finding on a set of
 * branches. Periods which overlap or touch — one ends at the instant the next one starts — merge,
 * with no gap tolerance: a reopening starts a new episode, and the gap between them belongs to no
 * episode. The stretches under an acceptance count as exposed.
 *
 * @property start Start of the earliest period of the episode
 * @property end End of the latest period of the episode, `null` while one of them is open
 */
data class FindingExposureEpisode(
    val start: LocalDateTime,
    val end: LocalDateTime?,
) {

    /**
     * Whether one of the periods of the episode is still open
     */
    val ongoing: Boolean get() = end == null

    /**
     * Length of the episode, up to [now] while it is ongoing
     */
    fun duration(now: LocalDateTime): Duration =
        Duration.between(start, end ?: now).coerceAtLeast(Duration.ZERO)

    companion object {

        /**
         * The episodes of some periods, the oldest first.
         *
         * @param periods Periods of one finding, on the branches to consider, in any order
         */
        fun of(periods: Collection<FindingExposurePeriod>): List<FindingExposureEpisode> {
            val episodes = mutableListOf<FindingExposureEpisode>()
            periods.sortedBy { it.startedAt }.forEach { period ->
                val current = episodes.lastOrNull()
                if (current != null && (current.end == null || period.startedAt <= current.end)) {
                    episodes[episodes.lastIndex] = current.copy(
                        end = if (current.end == null || period.endedAt == null) {
                            null
                        } else {
                            maxOf(current.end, period.endedAt)
                        }
                    )
                } else {
                    episodes += FindingExposureEpisode(start = period.startedAt, end = period.endedAt)
                }
            }
            return episodes
        }
    }
}
