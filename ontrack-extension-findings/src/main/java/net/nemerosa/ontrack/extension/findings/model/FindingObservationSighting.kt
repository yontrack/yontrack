package net.nemerosa.ontrack.extension.findings.model

/**
 * An observation, with the branch and the validation stamp of its run.
 */
data class FindingObservationSighting(
    val observation: FindingObservation,
    val branchId: Int,
    val validationStampId: Int,
)
