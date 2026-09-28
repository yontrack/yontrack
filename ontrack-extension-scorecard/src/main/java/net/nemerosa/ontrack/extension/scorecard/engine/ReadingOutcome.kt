package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason

/**
 * What a [computer][ReadingComputer] makes of its samples.
 */
data class ReadingOutcome(
    val value: Double?,
    val basis: ReadingBasis,
    val unknownReason: ReadingUnknownReason?,
    val details: Map<String, Any?>,
) {
    companion object {

        fun measured(value: Double, details: Map<String, Any?> = emptyMap()) = ReadingOutcome(
            value = value,
            basis = ReadingBasis.MEASURED,
            unknownReason = null,
            details = details,
        )

        fun unknown(reason: ReadingUnknownReason, details: Map<String, Any?> = emptyMap()) = ReadingOutcome(
            value = null,
            basis = ReadingBasis.UNKNOWN,
            unknownReason = reason,
            details = details,
        )
    }
}
