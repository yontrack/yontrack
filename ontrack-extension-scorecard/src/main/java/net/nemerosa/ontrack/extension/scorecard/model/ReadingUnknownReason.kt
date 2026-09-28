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

    /**
     * Time to restore with no failure in the window: nothing to restore. Rendered neutral rather
     * than as an unknown — a time to restore never reads 0.
     */
    NO_FAILURE,

    /**
     * Test reading with no test stamp — no validation stamp with the test summary data type — on the
     * branches in scope.
     */
    NO_TEST_STAMP,
}
