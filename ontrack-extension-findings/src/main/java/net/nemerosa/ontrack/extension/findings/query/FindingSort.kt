package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.Finding
import java.time.LocalDateTime

/**
 * Order of the findings of a project.
 */
enum class FindingSort {

    /**
     * The most severe first, then the most recently seen
     */
    DEFAULT,

    /**
     * The longest exposed first, by their [longest ongoing period][FindingExposedFor.ongoing],
     * then in the default order. The findings without any ongoing period, the resolved ones
     * among them, come last. Descending only.
     */
    EXPOSED_FOR;

    /**
     * Comparator of the findings for this order.
     *
     * @param exposedFor How long each finding has been exposed, by finding ID. Read for
     * [EXPOSED_FOR] only, where a finding missing from it has no ongoing period.
     */
    fun comparator(exposedFor: Map<Int, FindingExposedFor> = emptyMap()): Comparator<Finding> = when (this) {
        DEFAULT -> defaultOrder
        EXPOSED_FOR -> compareBy<Finding, LocalDateTime?>(nullsLast()) { exposedFor[it.id]?.ongoing?.startedAt }
            .then(defaultOrder)
    }

    companion object {
        /**
         * The most severe first, then the most recently seen.
         */
        private val defaultOrder: Comparator<Finding> =
            compareBy<Finding> { it.maxSeverity.ordinal }
                .thenByDescending { it.lastSeen }
                .thenBy { it.externalId }
                .thenBy { it.location }
                .thenBy { it.scanner }
    }
}
