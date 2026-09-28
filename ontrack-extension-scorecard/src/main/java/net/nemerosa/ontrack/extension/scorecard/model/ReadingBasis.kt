package net.nemerosa.ontrack.extension.scorecard.model

/**
 * What the value of a reading rests on.
 */
enum class ReadingBasis {
    /**
     * Measured from Yontrack's own data.
     */
    MEASURED,

    /**
     * Estimated. Never produced in 6.x: kept for the imported history of the ledger.
     */
    ESTIMATED,

    /**
     * Yontrack cannot see what the reading needs: the reading has no value, and
     * says why in its [unknown reason][ReadingUnknownReason].
     */
    UNKNOWN,
}
