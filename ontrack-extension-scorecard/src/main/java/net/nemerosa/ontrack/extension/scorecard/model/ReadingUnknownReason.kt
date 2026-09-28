package net.nemerosa.ontrack.extension.scorecard.model

/**
 * Why a reading is [unknown][ReadingBasis.UNKNOWN].
 */
enum class ReadingUnknownReason {
    /**
     * No marker to read up to: no promotion level on the branches in scope.
     */
    NO_MARKER,

    /**
     * Nothing reached the marker in the window.
     */
    NO_SAMPLES,
}
