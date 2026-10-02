package net.nemerosa.ontrack.extension.scorecard.model

/**
 * Why a reading is [unknown][ReadingBasis.UNKNOWN].
 */
enum class ReadingUnknownReason {
    /**
     * No marker to read up to: no promotion level on the branches in scope, or no slot of the
     * project in the marker environment, for the qualifier.
     */
    NO_MARKER,

    /**
     * Nothing to measure in the window: nothing reached the marker, or — for the remediation time — no
     * CRITICAL or HIGH finding was resolved.
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

    /**
     * Delivery reading up to an environment, without the licence of the environments.
     */
    NOT_LICENSED,

    /**
     * Security reading judged against the remediation targets of an estate, with no such target: no
     * estate, or an estate with neither a CRITICAL nor a HIGH target.
     */
    NO_TARGET,
}
